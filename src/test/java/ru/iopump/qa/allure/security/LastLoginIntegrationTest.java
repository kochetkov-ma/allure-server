package ru.iopump.qa.allure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.repo.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@link LastLoginListener}: verifies that a successful
 * Basic authentication stamps {@link UserEntity#getLastLoginAt()} so the admin
 * users grid can render the "Last login" column.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:last-login-test-db;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.security.require-api-auth=false",
    "basic.auth.enable=false",
    "gg.jte.development-mode=false",
    "gg.jte.use-precompiled-templates=true"
})
class LastLoginIntegrationTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin";
    private static final String API_REPORT_PATH = "/api/report";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("should stamp lastLoginAt on the user row after a successful Basic authentication")
    void basicAuth_stampsLastLoginAt() throws Exception {
        // GIVEN — the seeded admin has no lastLoginAt yet, and its default-password temporary flag
        // is cleared so the ApiTempPasswordGuardFilter permits the Basic call to /api/report
        // (an unrotated temporary password is blocked on the stateless surface — C1-5).
        final Instant before = Instant.now();
        userRepository.findByUsername(ADMIN_USER).ifPresent(u -> {
            u.setLastLoginAt(null);
            u.setPasswordTemporary(false);
            userRepository.save(u);
        });
        assertThat(userRepository.findByUsername(ADMIN_USER).orElseThrow().getLastLoginAt())
            .as("precondition: lastLoginAt must be cleared before authentication")
            .isNull();

        // WHEN — admin authenticates via Basic auth
        mockMvc.perform(get(API_REPORT_PATH)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(ADMIN_USER, ADMIN_PASS)))
            .andExpect(status().isOk());

        // THEN — lastLoginAt is populated and not before the request started
        final UserEntity refreshed = userRepository.findByUsername(ADMIN_USER).orElseThrow();
        assertThat(refreshed.getLastLoginAt())
            .as("lastLoginAt must be stamped after successful Basic authentication")
            .isNotNull()
            .isAfterOrEqualTo(before);
    }

    private static String basicAuthHeader(String username, String password) {
        final String credentials = username + ":" + password;
        final String encoded = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
