package ru.iopump.qa.allure.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iopump.qa.allure.entity.ReportEntity;

@Repository
public interface JpaReportRepository extends JpaRepository<ReportEntity, UUID> {
    @NonNull
    Optional<ReportEntity> findOneByUuid(@NonNull final UUID uuid);

    @NonNull
    Collection<ReportEntity> findByPath(@NonNull final String path);

    @NonNull
    Collection<ReportEntity> findByPathOrderByCreatedDateTimeDesc(@NonNull final String path);

    @NonNull
    Collection<ReportEntity> findByPathAndActiveFalseOrderByCreatedDateTimeDesc(@NonNull final String path);

    @NonNull
    Collection<ReportEntity> findByPathAndActiveTrueOrderByCreatedDateTimeDesc(@NonNull final String path);

    @NonNull
    Collection<ReportEntity> deleteByActiveFalse();

    @NonNull
    Collection<ReportEntity> findByActiveTrue();

    @NonNull
    Collection<ReportEntity> findAllByCreatedDateTimeIsBefore(@NonNull final LocalDateTime date);
}
