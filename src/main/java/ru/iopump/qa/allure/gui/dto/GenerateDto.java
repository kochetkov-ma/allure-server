package ru.iopump.qa.allure.gui.dto;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.iopump.qa.allure.service.PathUtil;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenerateDto {

    @NotEmpty
    @Pattern(regexp = PathUtil.UUID_PATTERN)
    String resultUuid;
    @Size(min = 2, max = 120)
    String path;
    @Size(min = 2, max = 120)
    String build;
    boolean deleteResults;
}
