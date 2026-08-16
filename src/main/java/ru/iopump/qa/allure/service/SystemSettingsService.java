package ru.iopump.qa.allure.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.iopump.qa.allure.entity.SystemSettingsEntity;
import ru.iopump.qa.allure.properties.AppSecurityProperties;
import ru.iopump.qa.allure.repo.SystemSettingsRepository;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the singleton {@link SystemSettingsEntity} row and caches an immutable
 * snapshot in memory for lock-free read access on the request hot path.
 * <p>
 * The cached value is consulted by {@code SecurityConfiguration}'s authorization
 * manager for {@code /api/**} on every request — a DB round-trip there would
 * measurably hurt CI throughput. Writes update the row inside a transaction and
 * then replace the cache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingsService implements ApplicationRunner {

    private final SystemSettingsRepository systemSettingsRepository;
    private final AppSecurityProperties appSecurityProperties;
    private final AtomicReference<Snapshot> cache = new AtomicReference<>();

    /**
     * Self-reference resolved through the AOP proxy so that {@link #seedIfAbsent()} and
     * {@link #readExisting()} are invoked transactionally from the non-transactional
     * {@link #run(ApplicationArguments)} entry point (a plain {@code this.} call would
     * bypass the proxy and the {@code @Transactional} advice).
     */
    @Lazy
    @Autowired
    private SystemSettingsService self;

    /**
     * Seeds the singleton settings row and primes the in-memory cache. Runs as an
     * {@link ApplicationRunner} so Spring invokes it through the transactional proxy
     * after the context is refreshed — {@link Transactional} therefore applies and the
     * read-or-insert executes atomically in one transaction. A {@code @PostConstruct}
     * method would bypass the proxy and run each repository call in its own transaction.
     * <p>
     * On a shared database two instances can both observe an empty table and race to
     * INSERT {@code SINGLETON_ID}; the loser's flush fails on the primary-key
     * constraint, which is caught and recovered by re-reading the now-present row in a
     * fresh transaction (so neither instance crashes on startup).
     */
    @Override
    public void run(ApplicationArguments args) {
        Snapshot snapshot;
        try {
            snapshot = self.seedIfAbsent();
        } catch (DataIntegrityViolationException raceLost) {
            log.info("System settings row was inserted concurrently — re-reading");
            snapshot = self.readExisting();
        }
        cache.set(snapshot);
        log.info("System settings loaded: {}", cache.get());
    }

    @Transactional
    Snapshot seedIfAbsent() {
        return systemSettingsRepository.findById(SystemSettingsEntity.SINGLETON_ID)
            .map(Snapshot::of)
            .orElseGet(this::insertDefaultRow);
    }

    @Transactional(readOnly = true)
    Snapshot readExisting() {
        return systemSettingsRepository.findById(SystemSettingsEntity.SINGLETON_ID)
            .map(Snapshot::of)
            .orElseGet(() -> new Snapshot(appSecurityProperties.requireApiAuth(), Instant.EPOCH, null));
    }

    private Snapshot insertDefaultRow() {
        final SystemSettingsEntity seeded = SystemSettingsEntity.builder()
            .id(SystemSettingsEntity.SINGLETON_ID)
            .requireApiAuth(appSecurityProperties.requireApiAuth())
            .updatedAt(Instant.now())
            .updatedByUsername(null)
            .build();
        log.info("Seeding system settings (requireApiAuth={})", seeded.isRequireApiAuth());
        return Snapshot.of(systemSettingsRepository.saveAndFlush(seeded));
    }

    public boolean isRequireApiAuth() {
        final Snapshot snapshot = cache.get();
        return snapshot != null && snapshot.requireApiAuth();
    }

    public Snapshot current() {
        final Snapshot snapshot = cache.get();
        if (snapshot != null) {
            return snapshot;
        }
        // Defensive: the ApplicationRunner seeding has not completed yet (e.g. a test
        // bean calls in before context startup finished).
        return new Snapshot(appSecurityProperties.requireApiAuth(), Instant.EPOCH, null);
    }

    @Transactional
    public Snapshot updateRequireApiAuth(boolean requireApiAuth, String actorUsername) {
        final SystemSettingsEntity entity = systemSettingsRepository.findById(SystemSettingsEntity.SINGLETON_ID)
            .orElseGet(() -> SystemSettingsEntity.builder()
                .id(SystemSettingsEntity.SINGLETON_ID)
                .build());
        entity.setRequireApiAuth(requireApiAuth);
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedByUsername(actorUsername);
        final SystemSettingsEntity saved = systemSettingsRepository.save(entity);
        final Snapshot snapshot = Snapshot.of(saved);
        // Publish to the lock-free read cache ONLY after the DB commit succeeds. Setting it
        // inside the transaction would leave the cache diverged from the persisted row if the
        // transaction later rolled back — /api/** could then fail OPEN on a stale snapshot.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.set(snapshot);
            }
        });
        log.info("System settings updated by '{}': requireApiAuth={}", actorUsername, requireApiAuth);
        return snapshot;
    }

    public record Snapshot(boolean requireApiAuth, Instant updatedAt, String updatedByUsername) {
        static Snapshot of(SystemSettingsEntity entity) {
            return new Snapshot(entity.isRequireApiAuth(), entity.getUpdatedAt(), entity.getUpdatedByUsername());
        }
    }
}
