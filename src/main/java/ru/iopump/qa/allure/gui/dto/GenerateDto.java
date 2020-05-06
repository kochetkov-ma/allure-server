package ru.iopump.qa.allure.gui.dto;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Data;
import ru.iopump.qa.allure.service.PathUtil;

@Data
public class GenerateDto {

    @NotEmpty
    @Pattern(regexp = PathUtil.UUID_PATTERN)
    String resultUuid;
    @Size(min = 2, max = 20)
    String path;
    @Size(min = 2, max = 20)
    String build;
    boolean deleteResults;
}
