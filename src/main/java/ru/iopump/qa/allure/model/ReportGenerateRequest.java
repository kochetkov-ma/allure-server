package ru.iopump.qa.allure.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import ru.iopump.qa.allure.service.PathUtil;

@Data
@NoArgsConstructor
public class ReportGenerateRequest {
    /**
     * Information about report. Will ba added to generated report.
     */
    @NotNull
    ReportSpec reportSpec;
    /**
     * Result UUID collection to generate new report.
     */
    @NotEmpty
    List<@Pattern(regexp = PathUtil.UUID_PATTERN) String> results;
    /**
     * Delete result after generation.
     */
    boolean deleteResults = true;

    @JsonIgnore
    public List<Path> getResultsAsPath(@NonNull Path baseResultDir) {
        return results.stream().map(p -> baseResultDir.resolve(Paths.get(p))).collect(Collectors.toUnmodifiableList());
    }
}
