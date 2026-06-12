package ru.iopump.qa.allure.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import ru.iopump.qa.allure.entity.ApiTokenEntity;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.ApiTokenRepository;
import ru.iopump.qa.allure.repo.UserRepository;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Slice test against the real Hibernate-generated schema (embedded H2 with the same
 * NOT NULL foreign key {@code app_api_token.user_id} that Postgres enforces). It proves
 * the F2 fix end-to-end: tokens must be removed before the owning user, otherwise the
 * delete fails on the FK constraint.
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserDeletionDataJpaTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiTokenRepository apiTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("should fail with DataIntegrityViolationException when deleting a user that still owns tokens")
    void deleteUser_withoutTokenCleanup_violatesForeignKey() {
        // GIVEN — a persisted user owning one API token
        final UserEntity owner = persistUser("token-owner");
        persistToken(owner, "ci-token");
        // Detach everything so the delete is issued as real SQL against the live FK, rather than
        // tripping Hibernate's in-context transient-association cascade check first.
        entityManager.clear();
        final UserEntity managedOwner = userRepository.findById(owner.getId()).orElseThrow();

        // WHEN — the user is deleted without first removing the token, forcing a flush through the
        // repository boundary (so Spring's PersistenceExceptionTranslation converts the native
        // Hibernate constraint violation into the spring-data DataIntegrityViolationException)
        // THEN — the NOT NULL FK on app_api_token.user_id rejects the orphaned row
        assertThatThrownBy(() -> {
            userRepository.delete(managedOwner);
            userRepository.flush();
        })
            .as("deleting a token-owning user without cleanup must violate the FK constraint")
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should delete the user successfully and remove all token rows when tokens are purged first")
    void deleteUser_afterTokenCleanup_succeedsAndRemovesTokens() {
        // GIVEN — a persisted user owning two API tokens (one of them revoked)
        final UserEntity owner = persistUser("token-owner");
        persistToken(owner, "active-token");
        final ApiTokenEntity revoked = persistToken(owner, "revoked-token");
        revoked.setRevokedAt(Instant.now());
        apiTokenRepository.saveAndFlush(revoked);

        assertThat(apiTokenRepository.findAllByUserIdOrderByCreatedAtDesc(owner.getId()))
            .as("precondition: the owner must have two token rows before cleanup")
            .hasSize(2);

        // WHEN — tokens are purged first (mirrors UserManagementService.delete order), then the user.
        // deleteAllByUserId is a bulk @Modifying query that bypasses the persistence context, so the
        // previously-loaded token entities stay managed; clear() evicts them before the user delete,
        // matching production where the user is loaded fresh and no token is attached.
        final int removed = apiTokenRepository.deleteAllByUserId(owner.getId());
        entityManager.clear();
        final UserEntity managedOwner = userRepository.findById(owner.getId()).orElseThrow();
        userRepository.delete(managedOwner);
        entityManager.flush();
        entityManager.clear();

        // THEN — both token rows and the user are gone, with no FK violation
        assertThat(removed)
            .as("deleteAllByUserId must report both token rows removed")
            .isEqualTo(2);
        assertThat(apiTokenRepository.findAllByUserIdOrderByCreatedAtDesc(owner.getId()))
            .as("no token rows may remain for the deleted user")
            .isEmpty();
        assertThat(userRepository.findById(owner.getId()))
            .as("the user row must be deleted")
            .isEmpty();
    }

    @Test
    @DisplayName("should report zero removed tokens when deleting a user that owns none")
    void deleteAllByUserId_returnsZero_whenUserHasNoTokens() {
        // GIVEN — a persisted user with no tokens
        final UserEntity owner = persistUser("tokenless");

        // WHEN — token cleanup runs for that user
        final int removed = apiTokenRepository.deleteAllByUserId(owner.getId());

        // THEN — nothing is removed and the user can still be deleted cleanly
        assertThat(removed)
            .as("deleteAllByUserId must remove zero rows for a tokenless user")
            .isEqualTo(0);
        userRepository.delete(owner);
        entityManager.flush();
        assertThat(userRepository.findById(owner.getId()))
            .as("the tokenless user row must be deleted")
            .isEmpty();
    }

    private UserEntity persistUser(String username) {
        // entityManager.persist (true JPA persist) is used instead of repository.save:
        // these entities carry an assigned UUID id with no @Version, so Spring Data's
        // isNew() heuristic treats them as detached and issues a merge — which returns a
        // managed copy while leaving the original reference detached, breaking the
        // @ManyToOne cascade check on flush.
        final UserEntity user = UserEntity.builder()
            .id(UUID.randomUUID())
            .username(username)
            .displayName(username)
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private ApiTokenEntity persistToken(UserEntity owner, String name) {
        final ApiTokenEntity token = ApiTokenEntity.builder()
            .id(UUID.randomUUID())
            .user(owner)
            .name(name)
            .tokenHash(UUID.randomUUID().toString().replace("-", ""))
            .tokenLookup(name.substring(0, Math.min(name.length(), 8)))
            .createdAt(Instant.now())
            .build();
        entityManager.persist(token);
        entityManager.flush();
        return token;
    }
}
