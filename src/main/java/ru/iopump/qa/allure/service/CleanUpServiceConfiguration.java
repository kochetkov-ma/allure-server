package ru.iopump.qa.allure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.properties.CleanUpProperties;
import ru.iopump.qa.allure.repo.JpaReportRepository;

import javax.annotation.PostConstruct;
import java.io.File;
import java.time.*;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.commons.io.FileUtils.deleteQuietly;

@Configuration
@Lazy(false)
@Slf4j
@Transactional
@RequiredArgsConstructor
@EnableScheduling
public class CleanUpServiceConfiguration implements SchedulingConfigurer {

    private final AllureProperties allureProperties;
    private final CleanUpProperties cleanUpProperties;
    private final JpaReportRepository repository;
    private final ObjectMapper objectMapper;

    private static String print(Collection<Pair<ReportEntity, Boolean>> removedReports) {
        return removedReports.stream().map(pair ->
                format("CleanUpResult(id=%s, path=%s, create=%s, age=%sd, isDeleted=%s)",
                        pair.getKey().getUuid(),
                        pair.getKey().getPath(),
                        pair.getKey().getCreatedDateTime(),
                        Duration.between(
                                pair.getKey().getCreatedDateTime(),
                                LocalDateTime.now()
                        ).toDays(),
                        pair.getValue())
        ).collect(Collectors.joining(", "));
    }

    @PostConstruct
    void init() throws JsonProcessingException {
        final ObjectWriter prettyWriter = objectMapper.writerWithDefaultPrettyPrinter();

        log.info("CleanUp policy settings:\n{}", prettyWriter.writeValueAsString(cleanUpProperties));
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(() -> {
            if (log.isInfoEnabled()) {
                log.info("CleanUp started ...");
                log.info("CleanUp parameters: " + cleanUpProperties);
            }

            final Collection<ReportEntity> candidatesCleanUp = repository
                    .findAllByCreatedDateTimeIsBefore(cleanUpProperties.getClosestEdgeDate());

            final Collection<Pair<ReportEntity, Boolean>> processedReports = candidatesCleanUp.stream()
                    .map(report ->
                            cleanUpProperties.getPaths().stream()
                                    // Есть ли среди настроек paths для данного отчета
                                    .filter(path -> report.getPath().equals(path.getPath())).findFirst()
                                    // Если отчет подпадает под правила paths, то найти правило и использовать
                                    .map(path -> {
                                        if (report.getCreatedDateTime().isBefore(path.getEdgeDate()))
                                            // Если отчет создан до крайней даты, то удалять
                                            return delete(report);
                                        else
                                            // Оставить если младше
                                            return Pair.of(report, false);
                                    })
                                    // Если отчет не подпадает под правила paths, то использовать общее правило ageDays
                                    .orElseGet(() -> {
                                        if (report.getCreatedDateTime().isBefore(cleanUpProperties.getEdgeDate()))
                                            // Если отчет создан до крайней даты, то удалять
                                            return delete(report);
                                        else
                                            // Оставить если младше
                                            return Pair.of(report, false);
                                    })
                    ).collect(Collectors.toUnmodifiableList());

            if (log.isInfoEnabled()) log.info("CleanUp finished with results: " + print(processedReports));

        }, triggerContext -> {

            final LocalDate nextDate = Optional.ofNullable(triggerContext.lastScheduledExecutionTime())
                    // Если триггер уже срабатывал, то прибавить день
                    .map(date -> date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1))
                    // Если триггер не срабатывал, то взять текущий день
                    .orElse(LocalDate.now());

            final LocalTime nextTime = cleanUpProperties.getTime();

            // Следующее срабатывание из даты и времени из настроек
            final LocalDateTime nextDateTime = LocalDateTime.of(nextDate, nextTime);

            if (log.isInfoEnabled()) log.info("Next CleanUp scheduled at " + nextDateTime);

            return Date.from(nextDateTime.atZone(ZoneId.systemDefault()).toInstant());
        });
    }

    private Pair<ReportEntity, Boolean> delete(ReportEntity report) {
        // Удаление из БД
        if (cleanUpProperties.isNotDryRun()) repository.delete(report);
        // Сформировать путь
        final File reportPath = allureProperties.reports().dirPath().resolve(report.getUuid().toString()).toFile();
        // Удалить и сохранить результат
        final boolean isDeleted;
        if (cleanUpProperties.isNotDryRun())
            isDeleted = deleteQuietly(reportPath);
        else
            isDeleted = true;

        return Pair.of(report, isDeleted);
    }
}
