package ru.iopump.qa.allure.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.qameta.allure.entity.ExecutorInfo;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

@Data
@NoArgsConstructor
public class ReportSpec {
    @NotEmpty
    String[] path;
    ExecutorInfo executorInfo;

    public static Path toPath(String... paths) {
        return Paths.get(paths[0], ArrayUtils.subarray(paths, 1, paths.length));
    }

    @JsonIgnore
    public Path getPathsAsPath() {
        return toPath(path);
    }

}
