package ru.iopump.qa.allure.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public final class AboutWebController {

    private static final String DEFAULT_VERSION = "dev";

    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    @GetMapping("/app/about")
    public String about(Model model) {
        final BuildProperties build = buildPropertiesProvider.getIfAvailable();
        model.addAttribute("title", "About");
        model.addAttribute("activeNav", "about");
        model.addAttribute("version", build == null ? DEFAULT_VERSION : build.getVersion());
        model.addAttribute("buildTime", build == null ? null : build.getTime());
        model.addAttribute("gitCommit", build == null ? null : build.get("git.commit"));
        return "about/index";
    }
}
