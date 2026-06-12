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
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the legacy {@code basic.auth.enable=true} backward-compatibility
 * mode (R2-1). Before the fix the legacy {@code anyRequest().authenticated()} rule was
 * shadowed by the earlier {@code /allure/**} permitAll and {@code /api/**} access-manager
 * matchers, so an upgrade that had relied on {@code basic.auth.enable} for protection
 * left {@code /api/**} anonymously writable and {@code /allure/**} anonymously readable.
 * <p>
 * These tests assert that in legacy mode {@code /api/**} and {@code /allure/**} both
 * require authentication, and that valid Basic credentials pass.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:legacy-basic-auth-test-db;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.security.require-api-auth=false",
    "basic.auth.enable=true",
    "gg.jte.development-mode=false",
    "gg.jte.use-precompiled-templates=true"
})
class LegacyBasicAuthIntegrationTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin";
    private static final String API_REPORT_PATH = "/api/report";
    private static final String ALLURE_PATH = "/allure/reports/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("should return 401 for anonymous GET /api/report when basic.auth.enable=true (legacy mode)")
    void apiReport_anonymousBlocked_inLegacyMode() throws Exception {
        // GIVEN — legacy basic.auth.enable=true; requireApiAuth is false but must not matter

        // WHEN — anonymous GET /api/report
        // THEN — 401: legacy mode re-gates /api/** to authenticated() despite requireApiAuth=false
        mockMvc.perform(get(API_REPORT_PATH))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 401 for anonymous GET /allure/** when basic.auth.enable=true (legacy mode)")
    void allure_anonymousBlocked_inLegacyMode() throws Exception {
        // GIVEN — legacy basic.auth.enable=true; report content must not be anonymously readable

        // WHEN — anonymous GET /allure/reports/
        // THEN — 401: legacy mode re-gates /allure/** to authenticated()
        mockMvc.perform(get(ALLURE_PATH))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should allow anonymous GET /icon.svg branding asset in legacy mode (not gated)")
    void iconSvg_anonymousAllowed_inLegacyMode() throws Exception {
        // GIVEN — legacy basic.auth.enable=true; the app favicon is a public branding asset
        // referenced by layout/main.jte and the Swagger branding filter

        // WHEN — anonymous GET /icon.svg
        // THEN — not 401: the shared always-public matcher whitelists /icon.svg in BOTH modes
        // (served from classpath:/static, so a successful read is 200)
        mockMvc.perform(get("/icon.svg"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should not gate anonymous GET /swagger/** branding assets to auth in legacy mode")
    void swaggerAssets_anonymousNotGated_inLegacyMode() throws Exception {
        // GIVEN — legacy basic.auth.enable=true; /swagger/** holds Swagger-UI branding assets
        // (theme.css, brand.js) that must load pre-auth so the Swagger UI renders

        // WHEN — anonymous GET of a /swagger/** asset
        // THEN — the request is NOT challenged with 401; the matcher permits it. A missing static
        // file resolves to 404, but crucially the legacy authenticated() gate does not apply.
        mockMvc.perform(get("/swagger/theme.css"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should allow Basic-authenticated admin GET /api/report when basic.auth.enable=true")
    void apiReport_withBasicAdmin_allowed_inLegacyMode() throws Exception {
        // GIVEN — bootstrap admin credentials, forced-rotation flag cleared so the basic
        // login is not bounced by the password-change filter on this API call
        clearTemporaryPassword(ADMIN_USER);
        final String basicAuth = basicAuthHeader(ADMIN_USER, ADMIN_PASS);

        // WHEN — authenticated GET /api/report
        // THEN — allowed (2xx): valid Basic creds satisfy the legacy authenticated() gate
        mockMvc.perform(get(API_REPORT_PATH)
                .header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().is2xxSuccessful());
    }

    ///// helpers /////

    private void clearTemporaryPassword(String username) {
        final UserEntity user = userRepository.findByUsername(username).orElseThrow();
        user.setPasswordTemporary(false);
        userRepository.save(user);
    }

    private static String basicAuthHeader(String username, String password) {
        final String credentials = username + ":" + password;
        final String encoded = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
