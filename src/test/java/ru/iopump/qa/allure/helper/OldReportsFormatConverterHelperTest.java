package ru.iopump.qa.allure.helper;

import java.io.IOException;
import java.util.Collection;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import ru.iopump.qa.allure.entity.ReportEntity;

public class OldReportsFormatConverterHelperTest {

    @Test
    public void convertFirstOldFormat() throws IOException {
        Resource resource = new ClassPathResource("old/allure/reports");
        OldReportsFormatConverterHelper helper = new OldReportsFormatConverterHelper(resource.getFile().toPath(), "reports");
        Collection<ReportEntity> collection = helper.convertOldFormat();
        Assertions.assertThat(collection).hasSizeGreaterThanOrEqualTo(4);

    }

    @Test
    public void hasOldFormatReports() throws IOException {
        Resource resource = new ClassPathResource("old/allure/reports");
        OldReportsFormatConverterHelper helper = new OldReportsFormatConverterHelper(resource.getFile().toPath(),
            "reports");
        Assertions.assertThat(helper.hasOldFormatReports()).isTrue();

        resource = new ClassPathResource("new");
        helper = new OldReportsFormatConverterHelper(resource.getFile().toPath(), "reports");
        Assertions.assertThat(helper.hasOldFormatReports()).isFalse();

    }
}