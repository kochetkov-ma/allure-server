package ru.iopump.qa.allure.entity;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportEntity {

    @Id
    private UUID uuid;
    @NotNull
    @Basic
    private java.time.LocalDateTime createdDateTime;
    @NotEmpty
    private String url;
    @NotEmpty
    private String path;
    @NotNull
    private boolean active;
    @Builder.Default
    @PositiveOrZero
    private long level = 0L; //NOPMD
    @Builder.Default
    @PositiveOrZero
    @Access(AccessType.PROPERTY)
    @Column(columnDefinition = "bigint not null default '0'")
    private long size = 0L; //NOPMD

    public static long sizeKB(@Nullable Path path) {
        if (path == null || Files.notExists(path)) {
            return 0L;
        }
        return FileUtils.sizeOfDirectory(path.toFile()) / 1024;
    }

    public static boolean checkUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }

        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public void setSize(Long size) {
        this.size = ObjectUtils.defaultIfNull(size, 0L);
    }

    public String generateUrl(String baseUrl) {
        return isFullUrl() ? url : baseUrl + url;
    }

    public boolean isFullUrl() {
        return checkUrl(url);
    }
}
