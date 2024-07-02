package ru.iopump.qa.allure.helper;

import io.qameta.allure.context.JacksonContext;
import io.qameta.allure.core.Configuration;
import io.qameta.allure.core.ResultsVisitor;
import io.qameta.allure.entity.ExecutorInfo;
import io.qameta.allure.executor.ExecutorPlugin;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class ExecutorCiPlugin extends ExecutorPlugin {
    public static final String JSON_FILE_NAME = "ci-executor.json";

    @Override
    public void readResults(Configuration configuration, ResultsVisitor visitor, Path directory) {
        final JacksonContext context = configuration.requireContext(JacksonContext.class);
        final Path nativeExecutorFile = directory.resolve("executor.json");

        if (Files.exists(nativeExecutorFile) && Files.isRegularFile(nativeExecutorFile))
            try {
                if (Files.readAllBytes(nativeExecutorFile).length > 1) return;
            } catch (IOException e) {
                log.error("Could not read existed native executor file {}", nativeExecutorFile, e);
            }

        final Path executorFile = directory.resolve(JSON_FILE_NAME);
        if (Files.exists(executorFile) && Files.isRegularFile(executorFile)) {
            try (InputStream is = Files.newInputStream(executorFile)) {
                final ExecutorInfo info = context.getValue().readValue(is, ExecutorInfo.class);
                visitor.visitExtra(EXECUTORS_BLOCK_NAME, info);
            } catch (IOException e) {
                visitor.error("Could not read executor file " + executorFile, e);
            }
        }
    }
}
