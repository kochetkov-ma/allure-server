package ru.iopump.qa.allure.properties;

import lombok.Getter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

import static java.time.LocalTime.MIDNIGHT;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Stream.concat;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@ConfigurationProperties(prefix = "allure.clean")
@Getter
@ToString
public class CleanUpProperties {

    private final boolean dryRun;
    private final LocalTime time;
    private final int ageDays;
    private final Collection<PathCleanUpItem> paths;
    private final int minAge;

    private final transient LocalDateTime closestEdgeDate;
    private final transient LocalDateTime edgeDate;

    public CleanUpProperties() {
        this(false, MIDNIGHT, 0, Collections.emptyList());
    }

    @ConstructorBinding
    public CleanUpProperties(boolean dryRun, LocalTime time, int ageDays, Collection<PathCleanUpItem> paths) {
        this.dryRun = defaultIfNull(dryRun, false);
        this.time = defaultIfNull(time, MIDNIGHT);
        this.ageDays = defaultIfNull(ageDays, 90);
        this.paths = defaultIfNull(paths, Collections.emptyList());

        this.minAge = concat(Stream.of(ageDays), this.paths.stream().map(PathCleanUpItem::getAgeDays)).min(naturalOrder()).orElse(0);
        this.closestEdgeDate = LocalDateTime.now().minusDays(this.minAge);
        this.edgeDate = LocalDateTime.now().minusDays(this.ageDays);
    }

    public boolean isNotDryRun() {
        return !dryRun;
    }

    @Getter
    @ToString
    public static class PathCleanUpItem {
        private final String path;
        private final int ageDays;

        private final transient LocalDateTime edgeDate;

        public PathCleanUpItem(String path, int ageDays) {
            this.path = path;
            this.ageDays = ageDays;
            this.edgeDate = LocalDateTime.now().minusDays(this.ageDays);
        }
    }
}
