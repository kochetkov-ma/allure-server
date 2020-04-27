package ru.iopump.qa.allure.entity;

import java.util.UUID;
import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private long level = 0L;
}
