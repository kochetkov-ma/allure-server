package ru.iopump.qa.allure.helper.plugin;

import io.qameta.allure.core.LaunchResults;
import org.springframework.beans.factory.BeanFactory;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.properties.TmsProperties;

import java.nio.file.Path;
import java.util.Collection;

public interface AllureServerPlugin {

    void onGenerationStart(Collection<Path> resultsDirectories, Context context);

    void onGenerationFinish(Path reportDirectory, Collection<LaunchResults> launchResults, Context context);

    String getName();

    interface Context {

        AllureProperties getAllureProperties();

        TmsProperties tmsProperties();

        BeanFactory beanFactory();

        String getReportUrl();
    }
}
