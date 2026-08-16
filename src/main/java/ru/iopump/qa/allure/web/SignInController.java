package ru.iopump.qa.allure.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Triggers the Basic-auth prompt and redirects to the reports page once the
 * browser has supplied credentials. {@code /app/signin} is configured as
 * {@code .authenticated()} in {@code SecurityConfiguration}, so reaching this
 * handler already implies a successful authentication.
 */
@Controller
public class SignInController {

    @GetMapping("/app/signin")
    public String signIn() {
        return "redirect:/app/reports";
    }
}
