package ru.iopump.qa.allure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@link ForcePasswordChangeFilter} through the real chain.
 * <p>
 * Verifies that a user whose {@code passwordTemporary} flag is {@code true} is
 * redirected to the forced password-change page on every {@code /app/**} request,
 * that the password-change page itself is NOT redirected (whitelist loop-prevention),
 * and that {@code /app/reports} is reachable again after the flag is cleared.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:force-pw-test-db;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.security.require-api-auth=false",
    "basic.auth.enable=false",
    "gg.jte.development-mode=false",
    "gg.jte.use-precompiled-templates=true"
})
class ForcePasswordChangeFilterIntegrationTest {

    private static final String TEMP_USER = "tempuser";
    private static final String TEMP_PLAIN = "TempPass1";
    private static final String NEW_PLAIN = "NewPassword99";
    private static final String FORCED_REDIRECT = ForcePasswordChangeFilter.REDIRECT_WITH_FLAG;
    private static final String PASSWORD_PAGE = ForcePasswordChangeFilter.TARGET;
    private static final String APP_REPORTS = "/app/reports";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedTempUser() {
        userRepository.findByUsername(TEMP_USER).ifPresent(userRepository::delete);
        userRepository.save(UserEntity.builder()
            .id(UUID.randomUUID())
            .username(TEMP_USER)
            .displayName("Temp User")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordHash(passwordEncoder.encode(TEMP_PLAIN))
            .passwordTemporary(true)
            .blocked(false)
            .mainAdmin(false)
            .build());
    }

    @Test
    @DisplayName("should redirect to /app/profile/password?forced=true when temp-password user accesses /app/reports")
    void seededUserWithTempPassword_redirectedFromAppPaths() throws Exception {
        // GIVEN — authenticated user with passwordTemporary=true
        final String basicAuth = basicAuthHeader(TEMP_USER, TEMP_PLAIN);

        // WHEN — GET /app/reports
        mockMvc.perform(get(APP_REPORTS)
                .header(HttpHeaders.AUTHORIZATION, basicAuth))
            // THEN — redirected to the forced password change page
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(FORCED_REDIRECT));
    }

    @Test
    @DisplayName("should NOT redirect temp-password user away from /app/profile/password (whitelist loop-prevention)")
    void tempPasswordUser_canReachPasswordPage() throws Exception {
        // GIVEN — temp-password user requests the change-password page itself
        final String basicAuth = basicAuthHeader(TEMP_USER, TEMP_PLAIN);

        // WHEN — GET /app/profile/password
        // THEN — served (200), NOT redirected to the forced page (no infinite loop)
        mockMvc.perform(get(PASSWORD_PAGE)
                .header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    @DisplayName("should serve /app/profile/password?forced=true to a temp-password user without re-redirect")
    void tempPasswordUser_canReachForcedPasswordPage() throws Exception {
        // GIVEN — temp-password user lands on the forced variant of the page
        final String basicAuth = basicAuthHeader(TEMP_USER, TEMP_PLAIN);

        // WHEN — GET /app/profile/password?forced=true
        // THEN — served (200), not redirected
        mockMvc.perform(get(PASSWORD_PAGE).param("forced", "true")
                .header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should serve /app/reports with 200 after the temp-password flag is cleared")
    void afterPasswordChange_appReportsAccessible() throws Exception {
        // GIVEN — user changes their password (clears the temp flag directly in DB for test isolation)
        userRepository.findByUsername(TEMP_USER).ifPresent(user -> {
            user.setPasswordHash(passwordEncoder.encode(NEW_PLAIN));
            user.setPasswordTemporary(false);
            userRepository.save(user);
        });
        final String basicAuth = basicAuthHeader(TEMP_USER, NEW_PLAIN);

        // WHEN — GET /app/reports after the flag is cleared
        // THEN — concretely 200 (no forced-redirect, no challenge)
        mockMvc.perform(get(APP_REPORTS)
                .header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    ///// helpers /////

    private static String basicAuthHeader(String username, String password) {
        final String credentials = username + ":" + password;
        final String encoded = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
