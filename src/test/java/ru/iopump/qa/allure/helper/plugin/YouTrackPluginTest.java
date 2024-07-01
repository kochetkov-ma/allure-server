package ru.iopump.qa.allure.helper.plugin;

import io.qameta.allure.DefaultLaunchResults;
import io.qameta.allure.core.LaunchResults;
import io.qameta.allure.entity.Link;
import io.qameta.allure.entity.TestResult;
import io.qameta.allure.entity.Time;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import ru.iopump.qa.allure.api.FeignConfiguration;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.properties.BasicProperties;
import ru.iopump.qa.allure.properties.CleanUpProperties;
import ru.iopump.qa.allure.properties.TmsProperties;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static io.qameta.allure.entity.Status.BROKEN;
import static io.qameta.allure.entity.Status.FAILED;
import static io.qameta.allure.entity.Status.PASSED;
import static java.util.Collections.emptyMap;

@SpringBootTest(classes = {FeignConfiguration.class})
@EnableConfigurationProperties({AllureProperties.class, CleanUpProperties.class, BasicProperties.class, TmsProperties.class})
public class YouTrackPluginTest {

    private final YouTrackPlugin plugin = new YouTrackPlugin(true);

    private final Collection<LaunchResults> launchResults = List.of(
        new DefaultLaunchResults(
            Set.of(
                new TestResult()
                    .setName("Scenario Name - 1")
                    .setFullName("Scenario Full Name - 1")
                    .setStatus(BROKEN)
                    .setTime(Time.create(10_000L))
                    .setLinks(List.of(
                        new Link().setName("ISSUE-100").setType("ISSUE").setUrl("https://tms.localhost/ISSUE-100"),
                        new Link().setName("ISSUE-101").setType("ISSUE").setUrl("https://tms.localhost/ISSUE-101"),
                        new Link().setName("NOT-ISSUE-101").setType("ISSUE").setUrl("https://not-tms.localhost/NOT-ISSUE-101")
                    )),

                new TestResult()
                    .setName("Scenario Name - 2")
                    .setFullName("Scenario Full Name - 2")
                    .setStatus(PASSED)
                    .setTime(Time.create(10_000L)),

                new TestResult()
                    .setName("Scenario Name - 3")
                    .setFullName("Scenario Full Name - 3")
                    .setStatus(FAILED)
                    .setTime(Time.create(10_000L))
                    .setLinks(List.of(
                        new Link().setName("ISSUE-100").setType("ISSUE").setUrl("https://tms.localhost/ISSUE-100"),
                        new Link().setName("").setType("ISSUE").setUrl("https://tms.localhost/ISSUE-666")
                    )),

                new TestResult()
                    .setName("Scenario Name - 4")
                    .setFullName("Scenario Full Name - 4")
                    .setStatus(PASSED)
                    .setTime(Time.create(10_000L))
                    .setLinks(List.of(
                        new Link().setName("ISSUE-100").setType("ISSUE").setUrl("https://tms.localhost/ISSUE-100")
                    )),

                new TestResult()
                    .setName("Scenario Name - 1")
                    .setFullName("Scenario Full Name - 1")
                    .setStatus(FAILED)
                    .setTime(Time.create(10_000L))
                    .setLinks(List.of(
                        new Link().setName("ISSUE-100").setType("ISSUE").setUrl("https://tms.localhost/ISSUE-100")
                    ))
            ),
            emptyMap(),
            emptyMap()
        )
    );

    @Test
    public void testOnGenerationFinish(@Autowired AllureProperties allureProperties, @Autowired TmsProperties tmsProperties, @Autowired BeanFactory beanFactory) {
        plugin.onGenerationFinish(Path.of("uuid", "report"), launchResults, new AllureServerPlugin.Context() {
            @Override
            public AllureProperties getAllureProperties() {
                return allureProperties;
            }

            @Override
            public TmsProperties tmsProperties() {
                return tmsProperties;
            }

            @Override
            public BeanFactory beanFactory() {
                return beanFactory;
            }

            @Override
            public String getReportUrl() {
                return "http://localhost:8080/";
            }
        });
    }
}
