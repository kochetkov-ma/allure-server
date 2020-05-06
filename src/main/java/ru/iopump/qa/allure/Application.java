package ru.iopump.qa.allure;

import com.vaadin.flow.spring.annotation.EnableVaadin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableAutoConfiguration
@EnableCaching
@EnableTransactionManagement
@EnableConfigurationProperties(AppCfg.class)
@EnableVaadin
public class Application { //NOPMD

    public static void main(String[] args) { //NOPMD
        SpringApplication.run(Application.class, args);
    }
}