package ru.iopump.qa.allure.repo;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
