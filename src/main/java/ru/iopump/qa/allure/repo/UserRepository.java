package ru.iopump.qa.allure.repo;

import jakarta.persistence.LockModeType;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iopump.qa.allure.entity.UserEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    @NonNull
    Optional<UserEntity> findByUsername(@NonNull String username);

    @NonNull
    List<UserEntity> findAllByOrderByUsernameAsc();

    @NonNull
    Optional<UserEntity> findByMainAdminTrue();

    /**
     * Load a user row under a pessimistic write lock. Used to serialize the
     * check-then-act token-cap enforcement in
     * {@link ru.iopump.qa.allure.service.ApiTokenService#createToken}: concurrent
     * creates for the same owner block on this row lock and run their count+insert
     * one at a time, so the per-user limit cannot be over-shot by a race.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") @NonNull UUID id);
}
