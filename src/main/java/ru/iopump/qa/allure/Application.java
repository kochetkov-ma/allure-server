package ru.iopump.qa.allure;

import lombok.experimental.UtilityClass;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@UtilityClass
@SpringBootApplication
@EnableCaching
public class Application {
    public void main(String[] args) { //NOPMD
        SpringApplication.run(Application.class, args);
    }
}