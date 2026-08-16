package ru.iopump.qa.allure.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.iopump.qa.allure.entity.SystemSettingsEntity;
import ru.iopump.qa.allure.properties.AppSecurityProperties;
import ru.iopump.qa.allure.repo.SystemSettingsRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    @Mock
    private SystemSettingsRepository systemSettingsRepository;

    @Mock
    private AppSecurityProperties appSecurityProperties;

    private SystemSettingsService systemSettingsService;

    @BeforeEach
    void setUp() {
        systemSettingsService = new SystemSettingsService(systemSettingsRepository, appSecurityProperties);
        // 'self' is normally the @Lazy AOP proxy injected by Spring so that seedIfAbsent()/
        // readExisting() run through the @Transactional advice; wiring it to the same instance
        // here is sufficient because unit tests trust Spring's own @Transactional plumbing and
        // only need self.xxx() to dispatch to the real method body.
        ReflectionTestUtils.setField(systemSettingsService, "self", systemSettingsService);
    }

    @Test
    @DisplayName("should insert a default row derived from the configured fallback when the settings table is empty")
    void seedIfAbsent_insertsDefaultRow_whenTableEmpty() {
        // GIVEN — an empty settings table and a configured fallback of true
        when(systemSettingsRepository.findById(SystemSettingsEntity.SINGLETON_ID)).thenReturn(Optional.empty());
        when(appSecurityProperties.requireApiAuth()).thenReturn(true);
        final SystemSettingsEntity saved = SystemSettingsEntity.builder()
            .id(SystemSettingsEntity.SINGLETON_ID)
            .requireApiAuth(true)
            .updatedAt(Instant.now())
            .build();
        when(systemSettingsRepository.saveAndFlush(any(SystemSettingsEntity.class))).thenReturn(saved);

        // WHEN
        final SystemSettingsService.Snapshot snapshot = systemSettingsService.seedIfAbsent();

        // THEN
        assertThat(snapshot.requireApiAuth())
            .as("seeded snapshot must carry the configured fallback default")
            .isTrue();

        final ArgumentCaptor<SystemSettingsEntity> captor = ArgumentCaptor.forClass(SystemSettingsEntity.class);
        verify(systemSettingsRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getId())
            .as("inserted row must use the well-known singleton id")
            .isEqualTo(SystemSettingsEntity.SINGLETON_ID);
        assertThat(captor.getValue().isRequireApiAuth())
            .as("inserted row must be seeded from the configured fallback default")
            .isTrue();
    }

    @Test
    @DisplayName("should return the existing row without inserting when the settings table already has a row")
    void seedIfAbsent_returnsExistingRow_whenAlreadyPresent() {
        // GIVEN
        final SystemSettingsEntity existingRow = SystemSettingsEntity.builder()
            .id(SystemSettingsEntity.SINGLETON_ID)
            .requireApiAuth(true)
            .updatedAt(Instant.EPOCH)
            .updatedByUsername("bob")
            .build();
        when(systemSettingsRepository.findById(SystemSettingsEntity.SINGLETON_ID)).thenReturn(Optional.of(existingRow));

        // WHEN
        final SystemSettingsService.Snapshot snapshot = systemSettingsService.seedIfAbsent();

        // THEN
        assertThat(snapshot)
            .as("snapshot must be derived from the existing row, not a freshly inserted one")
            .isEqualTo(new SystemSettingsService.Snapshot(true, Instant.EPOCH, "bob"));
        verify(systemSettingsRepository, never()).saveAndFlush(any(SystemSettingsEntity.class));
    }

    @Test
    @DisplayName("should recover by re-reading the row when a concurrent instance wins the seeding race")
    void run_recoversViaReReadingExistingRow_whenSeedRaceLost() {
        // GIVEN — first read finds no row (triggers an insert attempt), the insert then loses a
        // unique-constraint race, and a second read finds the row the concurrent winner inserted
        final SystemSettingsEntity raceWinnerRow = SystemSettingsEntity.builder()
            .id(SystemSettingsEntity.SINGLETON_ID)
            .requireApiAuth(true)
            .updatedAt(Instant.now())
            .build();
        when(systemSettingsRepository.findById(SystemSettingsEntity.SINGLETON_ID))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(raceWinnerRow));
        when(appSecurityProperties.requireApiAuth()).thenReturn(false);
        when(systemSettingsRepository.saveAndFlush(any(SystemSettingsEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // WHEN
        systemSettingsService.run(null);

        // THEN — cache reflects the concurrently-inserted row (true), not this instance's own
        // seeding attempt default (false)
        assertThat(systemSettingsService.current().requireApiAuth())
            .as("cache must reflect the concurrently-inserted row after race recovery")
            .isTrue();
    }

    @Test
    @DisplayName("should publish the new snapshot to the cache only after the enclosing transaction commits")
    void updateRequireApiAuth_publishesToCacheOnlyAfterCommit() {
        // GIVEN — an existing row, a cache pre-seeded to 'false', and an active transaction
        // synchronization (updateRequireApiAuth registers an afterCommit callback, which requires
        // synchronization to be active)
        final SystemSettingsEntity existingRow = SystemSettingsEntity.builder()
            .id(SystemSettingsEntity.SINGLETON_ID)
            .requireApiAuth(false)
            .updatedAt(Instant.EPOCH)
            .build();
        when(systemSettingsRepository.findById(SystemSettingsEntity.SINGLETON_ID)).thenReturn(Optional.of(existingRow));
        when(systemSettingsRepository.save(any(SystemSettingsEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        @SuppressWarnings("unchecked")
        final AtomicReference<SystemSettingsService.Snapshot> cacheRef =
            (AtomicReference<SystemSettingsService.Snapshot>) ReflectionTestUtils.getField(systemSettingsService, "cache");
        cacheRef.set(new SystemSettingsService.Snapshot(false, Instant.EPOCH, null));

        TransactionSynchronizationManager.initSynchronization();
        try {
            // WHEN
            systemSettingsService.updateRequireApiAuth(true, "alice");

            // THEN — before commit, the read cache must still reflect the pre-update value
            assertThat(systemSettingsService.isRequireApiAuth())
                .as("cache must not change before the transaction commits")
                .isFalse();

            // AND — once the registered synchronization's afterCommit fires, the cache updates
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
            assertThat(systemSettingsService.isRequireApiAuth())
                .as("cache must reflect the update once the transaction has committed")
                .isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("should fall back to the configured default when the cache has not been primed yet")
    void current_returnsConfiguredDefault_whenCacheUnset() {
        // GIVEN — a freshly constructed service whose ApplicationRunner has not executed yet
        when(appSecurityProperties.requireApiAuth()).thenReturn(true);

        // WHEN
        final SystemSettingsService.Snapshot snapshot = systemSettingsService.current();

        // THEN
        assertThat(snapshot)
            .as("fallback snapshot must mirror the configured default with no actor and the epoch timestamp")
            .isEqualTo(new SystemSettingsService.Snapshot(true, Instant.EPOCH, null));

        // AND — unlike current(), isRequireApiAuth() has no configured-default fallback and
        // reports false whenever the cache itself is unset
        assertThat(systemSettingsService.isRequireApiAuth())
            .as("isRequireApiAuth() must return false when the cache is unset, independent of the configured default")
            .isFalse();
    }
}
