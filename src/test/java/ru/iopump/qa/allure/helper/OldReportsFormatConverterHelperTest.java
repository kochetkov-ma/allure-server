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
        OldReportsFormatConverterHelper helper = new OldReportsFormatConverterHelper(resource.getFile().toPath(),
            "reports",
            "http://localhost");
        Collection<ReportEntity> collection = helper.convertOldFormat();
        Assertions.assertThat(collection).hasSize(5);

    }

    @Test
    public void hasOldFormatReports() throws IOException {
        Resource resource = new ClassPathResource("old/allure/reports");
        OldReportsFormatConverterHelper helper = new OldReportsFormatConverterHelper(resource.getFile().toPath(),
            "reports",
            "http://localhost");
        Assertions.assertThat(helper.hasOldFormatReports()).isTrue();

        resource = new ClassPathResource("new");
        helper = new OldReportsFormatConverterHelper(resource.getFile().toPath(),
            "reports",
            "http://localhost");
        Assertions.assertThat(helper.hasOldFormatReports()).isFalse();

    }
}