package ru.iopump.qa.allure.web.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.iopump.qa.allure.service.PathUtil;

import java.util.List;

/**
 * Form-backing DTO for {@code POST /app/results/generate}. Mirrors the "Generate report"
 * dialog from the JTE results page — exposes the <strong>full</strong> Allure
 * {@code ExecutorInfo} contract (name, type, url, build*, report*).
 * <p>
 * REST wire compatibility is preserved at the {@code /api/report} layer — this form is
 * translated by the controller into the existing {@code ReportGenerateRequest} +
 * {@code ReportSpec(executorInfo=...)} shape. All executor fields are optional so that
 * a minimal submission (just {@code resultUuids} + {@code reportPath}) still generates.
 *
 * @param resultUuids   selected result UUIDs to feed into the generator; at least one required
 * @param reportPath    logical report path segments (e.g. {@code ["branch", "job"]}); 1..32
 * @param executorName  optional {@code ExecutorInfo#name}
 * @param executorType  optional {@code ExecutorInfo#type}
 * @param executorUrl   optional {@code ExecutorInfo#url}
 * @param buildName     optional {@code ExecutorInfo#buildName}
 * @param buildUrl      optional {@code ExecutorInfo#buildUrl}
 * @param reportUrl     optional {@code ExecutorInfo#reportUrl}
 * @param reportName    optional {@code ExecutorInfo#reportName}
 * @param deleteResults whether to delete source results after successful generation
 */
public record GenerateForm(
    @NotEmpty(message = "select at least one result")
    List<@NotBlank(message = "result UUID must not be blank")
         @Pattern(regexp = PathUtil.UUID_PATTERN, message = "result UUID must match UUID pattern") String> resultUuids,

    @NotEmpty(message = "reportPath must not be empty")
    @Size(max = 32, message = "reportPath must contain at most 32 segments")
    List<@NotBlank(message = "report path segment must not be blank") String> reportPath,

    @Nullable String executorName,
    @Nullable String executorType,
    @Nullable String executorUrl,
    @Nullable String buildName,
    @Nullable String buildUrl,
    @Nullable String reportUrl,
    @Nullable String reportName,

    boolean deleteResults
) {
}
