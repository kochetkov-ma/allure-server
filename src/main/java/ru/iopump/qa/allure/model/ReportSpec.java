package ru.iopump.qa.allure.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Joiner;
import io.qameta.allure.entity.ExecutorInfo;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

@Data
@NoArgsConstructor
public class ReportSpec {
    @NotEmpty(message = "path must not be empty")
    @Size(max = 32, message = "path must contain at most 32 segments")
    String[] path;

    ExecutorInfo executorInfo;

    public static String toPath(String... paths) {
        return Joiner.on("/").join(paths);
    }

    @JsonIgnore
    public String getPathsAsPath() {
        return toPath(path);
    }

    /**
     * Bean-validation guard: every element of {@link #path} must be non-blank.
     */
    @JsonIgnore
    @AssertTrue(message = "path segments must not be blank")
    public boolean isPathSegmentsNotBlank() {
        return path == null || Arrays.stream(path).allMatch(StringUtils::isNotBlank);
    }
}
