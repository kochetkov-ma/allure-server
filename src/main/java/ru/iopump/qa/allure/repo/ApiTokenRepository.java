package ru.iopump.qa.allure.repo;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iopump.qa.allure.entity.ApiTokenEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiTokenEntity, UUID> {

    @NonNull
    Collection<ApiTokenEntity> findByTokenLookup(@NonNull String tokenLookup);

    @NonNull
    List<ApiTokenEntity> findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(@NonNull UUID userId);

    @NonNull
    List<ApiTokenEntity> findAllByUserIdOrderByCreatedAtDesc(@NonNull UUID userId);

    @NonNull
    Optional<ApiTokenEntity> findByIdAndUserId(@NonNull UUID id, @NonNull UUID userId);

    @Query("select count(t) from ApiTokenEntity t"
        + " where t.user.id = :userId"
        + " and t.revokedAt is null"
        + " and (t.expiresAt is null or t.expiresAt > :now)")
    long countActiveByUserId(@Param("userId") @NonNull UUID userId,
                             @Param("now") @NonNull Instant now);

    @Modifying
    @Query("delete from ApiTokenEntity t where t.user.id = :userId")
    int deleteAllByUserId(@Param("userId") @NonNull UUID userId);
}
