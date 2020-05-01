package ru.iopump.qa.allure.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Joiner;
import io.qameta.allure.entity.ExecutorInfo;
import javax.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReportSpec {
    @NotEmpty
    String[] path;
    ExecutorInfo executorInfo;

    public static String toPath(String... paths) {
        return Joiner.on("/").join(paths);
    }

    @JsonIgnore
    public String getPathsAsPath() {
        return toPath(path);
    }
}