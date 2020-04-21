package ru.iopump.qa.allure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class Application { //NOPMD

    public static void main(String[] args) { //NOPMD
        SpringApplication.run(Application.class, args);
    }
}