package ru.iopump.qa.allure.entity;

import java.util.UUID;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToOne;
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
    @Basic
    private java.time.LocalDateTime createdDateTime;
    private String url;
    private String path;
    private boolean active;
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private ReportEntity childReport;
}
