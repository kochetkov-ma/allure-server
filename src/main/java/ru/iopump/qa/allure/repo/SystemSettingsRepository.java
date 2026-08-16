package ru.iopump.qa.allure.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iopump.qa.allure.entity.SystemSettingsEntity;

import java.util.UUID;

@Repository
public interface SystemSettingsRepository extends JpaRepository<SystemSettingsEntity, UUID> {
}
