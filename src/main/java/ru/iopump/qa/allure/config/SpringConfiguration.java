package ru.iopump.qa.allure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.iopump.qa.allure.helper.plugin.AllureServerPlugin;
import ru.iopump.qa.util.ReflectionUtil;

import java.util.Collection;

@Slf4j
@Configuration
public class SpringConfiguration {

    @Bean
    public Collection<AllureServerPlugin> allureServerPlugins() {
        var plugins = ReflectionUtil.createImplementations(AllureServerPlugin.class, null);
        log.info("[ALLURE SERVER CONFIGURATION] Allure server plugins loaded: {}", plugins.stream().map(SpringConfiguration::name).toList());
        return plugins;
    }

    private static String name(AllureServerPlugin plugin) {
        return plugin.getClass() + ":" + plugin.getName();
    }
}
