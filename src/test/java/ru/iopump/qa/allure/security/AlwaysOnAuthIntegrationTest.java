package ru.iopump.qa.allure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.UserRepository;
import ru.iopump.qa.allure.service.ApiTokenService;
import ru.iopump.qa.allure.service.SystemSettingsService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test for the security filter chain.
 * <p>
 * Exercises, through the real {@code SecurityFilterChain}:
 * <ul>
 *   <li>Always-on auth + {@code requireApiAuth} toggle for {@code /api/**}.</li>
 *   <li>X-API-Token authentication for {@code /api/**} (the flagship CI flow).</li>
 *   <li>Anonymous guest read access vs. authenticated write access on {@code /app/**}.</li>
 *   <li>Admin-only enforcement on {@code /app/admin/**} (method security active).</li>
 *   <li>The guest-token-bypass defense for the {@code requireApiAuth} toggle.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:always-on-auth-test-db;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.security.require-api-auth=false",
    "basic.auth.enable=false",
    "gg.jte.development-mode=false",
    "gg.jte.use-precompiled-templates=true"
})
class AlwaysOnAuthIntegrationTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin";
    private static final String API_REPORT_PATH = "/api/report";
    private static final String APP_REPORTS_PATH = "/app/reports";
    private static final String APP_ADMIN_USERS = "/app/admin/users";
    private static final String APP_RESULTS_PATH = "/app/results";
    private static final String APP_RESULTS_UPLOAD_PATH = "/app/results/upload";
    private static final String ALLURE_RESULTS_FIXTURE = "allure-results.zip";
    private static final String FLASH_KEY = "flash";
    private static final String FLASH_LEVEL_KEY = "level";
    private static final String LEVEL_SUCCESS = "success";
    private static final String USER_NAME = "regular";
    private static final String USER_PASS = "UserPass99";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemSettingsService systemSettingsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiTokenService apiTokenService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    @DisplayName("should allow anonymous GET /api/report when requireApiAuth is false (default)")
    void apiReport_anonymousAllowed_whenRequireApiAuthFalse() throws Exception {
        // GIVEN — requireApiAuth is false (bootstrap default, confirmed by property source)
        assertThat(systemSettingsService.isRequireApiAuth())
            .as("test precondition: requireApiAuth must be false for this test")
            .isFalse();

        // WHEN — anonymous GET /api/report
        mockMvc.perform(get(API_REPORT_PATH))
            // THEN — allowed (200)
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 401 for anonymous GET /api/report when requireApiAuth is toggled to true")
    void apiReport_anonymousBlocked_whenRequireApiAuthTrue() throws Exception {
        // GIVEN — toggle requireApiAuth to true at runtime
        systemSettingsService.updateRequireApiAuth(true, "test-actor");
        try {
            assertThat(systemSettingsService.isRequireApiAuth())
                .as("requireApiAuth must be true after the update")
                .isTrue();

            // WHEN — anonymous GET /api/report
            mockMvc.perform(get(API_REPORT_PATH))
                // THEN — 401 Unauthorized
                .andExpect(status().isUnauthorized());
        } finally {
            systemSettingsService.updateRequireApiAuth(false, "test-actor");
        }
    }

    @Test
    @DisplayName("should allow authenticated Basic admin GET /api/report regardless of requireApiAuth flag")
    void apiReport_withBasicAdmin_allowed() throws Exception {
        // GIVEN — admin credentials from bootstrap seeder, with the forced-rotation flag cleared:
        // the default admin/admin password is seeded as temporary and the ApiTempPasswordGuardFilter
        // now blocks a temporary password on /api/** (C1-5), so a legitimately-onboarded admin has
        // already rotated.
        clearTemporaryPassword(ADMIN_USER);
        final String basicAuth = basicAuthHeader(ADMIN_USER, ADMIN_PASS);

        // WHEN — authenticated GET /api/report
        mockMvc.perform(get(API_REPORT_PATH)
                .header(HttpHeaders.AUTHORIZATION, basicAuth))
            // THEN — allowed (2xx)
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("should return 403 for Basic /api/report when the password is still temporary (C1-5)")
    void apiReport_withTempPasswordBasic_forbidden() throws Exception {
        // GIVEN — a seeded USER whose passwordTemporary flag is still set (default/admin-issued
        // password not yet rotated). Such a principal must not reach the stateless API surface via
        // Basic before rotating (finding C1-5).
        final String tempName = "tempapiuser";
        final String tempPass = "TempApiPass1";
        userRepository.findByUsername(tempName).ifPresent(userRepository::delete);
        userRepository.save(UserEntity.builder()
            .id(UUID.randomUUID())
            .username(tempName)
            .displayName(tempName)
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordHash(passwordEncoder.encode(tempPass))
            .passwordTemporary(true)
            .blocked(false)
            .mainAdmin(false)
            .build());

        // WHEN — the temp-password user authenticates via Basic on /api/report
        // THEN — 403: authentication succeeds but the guard blocks API access until rotation
        mockMvc.perform(get(API_REPORT_PATH)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(tempName, tempPass)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 200 for anonymous GET /app/reports (guest read-only UI mode)")
    void appReports_anonymousAllowed() throws Exception {
        // GIVEN — anonymous request; /app/** GET is permitAll in guest read-only mode

        // WHEN — anonymous GET /app/reports
        // THEN — concretely 200 (page rendered for guest)
        mockMvc.perform(get(APP_REPORTS_PATH))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should reject anonymous POST /app/reports/upload with 401 (mutation requires auth)")
    void appReportsUpload_anonymousRejected() throws Exception {
        // GIVEN — anonymous request to a mutating /app handler

        // WHEN — anonymous POST /app/reports/upload (with a valid CSRF token so the request is
        // rejected by authorization, not the CSRF filter)
        // THEN — challenged with 401 (anonymous mutation not allowed)
        mockMvc.perform(post("/app/reports/upload").with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should redirect (never 403) an authenticated real multipart POST /app/results/upload carrying a valid CSRF token")
    void resultsUpload_realMultipartWithValidCsrf_notForbidden() throws Exception {
        // GIVEN — an authenticated admin (temp-password rotation cleared so ForcePasswordChangeFilter
        // does not intercept) posting a genuine multipart/form-data request, matching the browser
        // upload form's enctype and its '_csrf' hidden field (partials/csrf.jte). This exercises the
        // real HiddenHttpMethodFilter + CsrfFilter interplay on the multipart body, which the plain
        // (non-multipart) posts elsewhere in this class do not cover.
        clearTemporaryPassword(ADMIN_USER);
        final String basicAuth = basicAuthHeader(ADMIN_USER, ADMIN_PASS);
        final MockMultipartFile zipFile = new MockMultipartFile(
            "file", ALLURE_RESULTS_FIXTURE, "application/zip",
            new ClassPathResource(ALLURE_RESULTS_FIXTURE).getInputStream());

        // WHEN — the multipart POST carries a valid CSRF token (SecurityMockMvcRequestPostProcessors#csrf
        // submits it through the same request-parameter channel the rendered hidden field would use)
        final MvcResult result = mockMvc.perform(multipart(APP_RESULTS_UPLOAD_PATH)
                .file(zipFile)
                .header(HttpHeaders.AUTHORIZATION, basicAuth)
                .with(csrf()))
            // THEN — never 403: the controller reaches its handler and always redirects
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(APP_RESULTS_PATH))
            .andReturn();

        // AND — the upload genuinely succeeded end-to-end, not merely swallowed into an error redirect
        @SuppressWarnings("unchecked")
        final Map<String, Object> flash = (Map<String, Object>) result.getFlashMap().get(FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("multipart upload with a valid CSRF token must succeed through the real filter chain")
            .isEqualTo(LEVEL_SUCCESS);
    }

    @Test
    @DisplayName("should return 403 for a real multipart POST /app/results/upload with no CSRF token")
    void resultsUpload_realMultipartWithoutCsrf_forbidden() throws Exception {
        // GIVEN — the same authenticated multipart request as the positive case (valid zip file
        // part, admin Basic credentials) but with no '_csrf' part or header at all
        clearTemporaryPassword(ADMIN_USER);
        final String basicAuth = basicAuthHeader(ADMIN_USER, ADMIN_PASS);
        final MockMultipartFile zipFile = new MockMultipartFile(
            "file", ALLURE_RESULTS_FIXTURE, "application/zip",
            new ClassPathResource(ALLURE_RESULTS_FIXTURE).getInputStream());

        // WHEN — the multipart POST omits the CSRF token
        // THEN — CsrfFilter rejects the request before authorization is even evaluated
        mockMvc.perform(multipart(APP_RESULTS_UPLOAD_PATH)
                .file(zipFile)
                .header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should reject anonymous POST /app/profile/tokens with 401 (guest cannot mint API tokens)")
    void appProfileTokens_anonymousRejected() throws Exception {
        // GIVEN — anonymous visitor (resolves to seeded guest in the controller layer)

        // WHEN — anonymous POST /app/profile/tokens (valid CSRF token so authorization is the gate)
        // THEN — chain blocks before the controller; 401 challenge
        mockMvc.perform(post("/app/profile/tokens").with(csrf()).param("name", "evil").param("ttl", ""))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should reject anonymous POST /app/profile/password with 401 (mutation requires auth)")
    void appProfilePassword_anonymousRejected() throws Exception {
        // GIVEN — anonymous visitor (resolves to seeded guest in the controller layer)

        // WHEN — anonymous POST /app/profile/password
        // THEN — chain blocks before the controller; 401 challenge (not protected by accident
        // of the guest row having no password hash)
        mockMvc.perform(post("/app/profile/password")
                .with(csrf())
                .param("currentPassword", "x")
                .param("newPassword", "y")
                .param("confirmPassword", "y"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should refuse to mint an API token for the guest principal (F4 /app-mutation bypass closed at the service boundary)")
    void appProfilePassword_guestTokenRejected() {
        // GIVEN — the seeded guest principal (ROLE_GUEST)
        final UserEntity guest = userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME).orElseThrow();

        // WHEN / THEN — a guest can no longer obtain a token, so it can never authenticate to
        // attempt a password rotation. The guest-token bypass is now closed at creation time
        // (ApiTokenService), ahead of the chain's ROLE_GUEST rejection in mutationAuthorizationManager.
        assertThatThrownBy(() -> apiTokenService.createToken(guest, "guest-pw-token", null))
            .as("guest accounts must not be able to own API tokens (mutation-surface bypass closed at source)")
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should pass an authenticated non-guest USER through the chain on POST /app/profile/password")
    void appProfilePassword_authenticatedNonGuestAllowedThroughChain() throws Exception {
        // GIVEN — a seeded non-admin USER with a local password and the temp flag cleared so the
        // ForcePasswordChangeFilter does not bounce the request
        seedUser(USER_NAME, USER_PASS, UserRole.USER);
        final String basicAuth = basicAuthHeader(USER_NAME, USER_PASS);

        // WHEN — authenticated non-guest POST /app/profile/password
        // THEN — the mutation matcher PERMITS the non-guest principal, so the request reaches the
        // controller (not 401, not 403). The controller re-renders/redirects on validation outcome.
        mockMvc.perform(post("/app/profile/password")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, basicAuth)
                .param("currentPassword", USER_PASS)
                .param("newPassword", "NewUserPass1")
                .param("confirmPassword", "NewUserPass1"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("should allow a temp-password (forced-rotation) USER through the chain on POST /app/profile/password")
    void appProfilePassword_forcedRotationUserAllowedThroughChain() throws Exception {
        // GIVEN — a seeded USER whose passwordTemporary flag is still set (forced-rotation state).
        // Such a user is an authenticated, NON-guest principal, so the new mutation matcher must
        // still permit the legit forced-rotation POST (regression guard for the password matcher).
        final String forcedName = "forceduser";
        final String forcedPass = "ForcedPass1";
        userRepository.findByUsername(forcedName).ifPresent(userRepository::delete);
        userRepository.save(UserEntity.builder()
            .id(UUID.randomUUID())
            .username(forcedName)
            .displayName(forcedName)
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordHash(passwordEncoder.encode(forcedPass))
            .passwordTemporary(true)
            .blocked(false)
            .mainAdmin(false)
            .build());
        final String basicAuth = basicAuthHeader(forcedName, forcedPass);

        // WHEN — the forced-rotation user POSTs the new password
        // THEN — the matcher permits (non-guest), the request reaches the controller and redirects.
        // A 401/403 here would mean the matcher wrongly blocked the legit rotation flow.
        mockMvc.perform(post("/app/profile/password")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, basicAuth)
                .param("currentPassword", forcedPass)
                .param("newPassword", "RotatedPass1")
                .param("confirmPassword", "RotatedPass1"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("should register ApiTokenAuthenticationFilter with a disabled FilterRegistrationBean")
    void apiTokenFilter_registrationDisabled() {
        // GIVEN — the filter is a @Component extending OncePerRequestFilter; the container would
        // otherwise auto-register it in the main servlet chain (double execution).

        // WHEN — resolving the explicit FilterRegistrationBean from the context
        @SuppressWarnings("unchecked")
        final org.springframework.boot.web.servlet.FilterRegistrationBean<ApiTokenAuthenticationFilter> registration =
            applicationContext.getBean("apiTokenFilterRegistration",
                org.springframework.boot.web.servlet.FilterRegistrationBean.class);

        // THEN — it is present and disabled so the filter runs ONLY inside the security chain
        assertThat(registration.isEnabled())
            .as("ApiTokenAuthenticationFilter servlet auto-registration must be disabled")
            .isFalse();
    }

    @Test
    @DisplayName("should authenticate /api/report with a valid X-API-Token when requireApiAuth is true")
    void apiReport_withValidApiToken_allowed() throws Exception {
        // GIVEN — a real persisted token for the seeded admin, requireApiAuth on
        final UserEntity admin = userRepository.findByUsername(ADMIN_USER).orElseThrow();
        final ApiTokenService.TokenIssueResult issued =
            apiTokenService.createToken(admin, "ci-pipeline", null);
        final String plainToken = issued.plainToken();
        systemSettingsService.updateRequireApiAuth(true, "test-actor");
        try {
            // WHEN — GET /api/report with the X-API-Token header
            mockMvc.perform(get(API_REPORT_PATH)
                    .header(ApiTokenAuthenticationFilter.HEADER_NAME, plainToken))
                // THEN — authenticated through the real chain, allowed
                .andExpect(status().is2xxSuccessful());
        } finally {
            apiTokenService.revoke(admin, issued.entityId());
            systemSettingsService.updateRequireApiAuth(false, "test-actor");
        }
    }

    @Test
    @DisplayName("should return 401 for /api/report with a garbage X-API-Token")
    void apiReport_withGarbageApiToken_rejected() throws Exception {
        // GIVEN — requireApiAuth is irrelevant: the token filter rejects any present-but-invalid token

        // WHEN — GET /api/report with a syntactically valid prefix but unknown token
        // THEN — 401, never falls through to anonymous
        mockMvc.perform(get(API_REPORT_PATH)
                .header(ApiTokenAuthenticationFilter.HEADER_NAME, "bqa_thisisnotarealtokenvalue00000"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 401 for /api/report with a revoked X-API-Token when requireApiAuth is true")
    void apiReport_withRevokedApiToken_rejected() throws Exception {
        // GIVEN — a token that is created then revoked, requireApiAuth on
        final UserEntity admin = userRepository.findByUsername(ADMIN_USER).orElseThrow();
        final ApiTokenService.TokenIssueResult issued =
            apiTokenService.createToken(admin, "to-revoke", null);
        apiTokenService.revoke(admin, issued.entityId());
        systemSettingsService.updateRequireApiAuth(true, "test-actor");
        try {
            // WHEN — GET /api/report with the revoked token
            // THEN — 401
            mockMvc.perform(get(API_REPORT_PATH)
                    .header(ApiTokenAuthenticationFilter.HEADER_NAME, issued.plainToken()))
                .andExpect(status().isUnauthorized());
        } finally {
            systemSettingsService.updateRequireApiAuth(false, "test-actor");
        }
    }

    @Test
    @DisplayName("should refuse to mint an API token for the guest principal (F4 /api bypass closed at the service boundary)")
    void apiReport_withGuestToken_forbidden() {
        // GIVEN — the seeded guest principal (ROLE_GUEST)
        final UserEntity guest = userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME).orElseThrow();

        // WHEN / THEN — the guest-token bypass is now closed at creation time: a token bound to the
        // shared GUEST principal can never be minted, so it can never unlock the protected /api/**
        // surface. Defense-in-depth remains in the chain (apiAuthorizationManager rejects ROLE_GUEST).
        assertThatThrownBy(() -> apiTokenService.createToken(guest, "guest-token", null))
            .as("guest accounts must not be able to own API tokens (API-surface bypass closed at source)")
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should return 401 for anonymous GET /app/admin/users (admin surface is not public)")
    void adminUsers_anonymousRejected() throws Exception {
        // GIVEN — anonymous request to the admin surface

        // WHEN — anonymous GET /app/admin/users
        // THEN — 401 (method security denies anonymous, Basic entry point challenges)
        mockMvc.perform(get(APP_ADMIN_USERS))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 403 for a non-admin USER GET /app/admin/users")
    void adminUsers_regularUserForbidden() throws Exception {
        // GIVEN — a seeded non-admin USER with a local password
        seedUser(USER_NAME, USER_PASS, UserRole.USER);
        final String basicAuth = basicAuthHeader(USER_NAME, USER_PASS);

        // WHEN — USER GET /app/admin/users
        // THEN — 403 forbidden (hasRole('ADMIN') not satisfied)
        mockMvc.perform(get(APP_ADMIN_USERS)
                .header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 200 for admin GET /app/admin/users")
    void adminUsers_adminAllowed() throws Exception {
        // GIVEN — bootstrap admin credentials, with the forced-rotation flag cleared so the
        // ForcePasswordChangeFilter does not bounce the admin to the password-change page
        // (the bootstrap admin/admin password is seeded as temporary by design).
        clearTemporaryPassword(ADMIN_USER);
        final String basicAuth = basicAuthHeader(ADMIN_USER, ADMIN_PASS);

        // WHEN — admin GET /app/admin/users
        // THEN — 200
        mockMvc.perform(get(APP_ADMIN_USERS)
                .header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should redirect POST /logout to the existing /app/reports route (302, not 404)")
    void logout_redirectsToMappedRoute() throws Exception {
        // GIVEN — the user menu renders POST /logout; the success URL must be a mapped route

        // WHEN — POST /logout as a browser (Accept: text/html); Spring Security negotiates the
        // logout response by content type — a browser navigation gets a 302 redirect to the
        // configured success URL, whereas a REST/AJAX client (no html Accept) gets a 204.
        // THEN — 302 to /app/reports (no raw 404 from an unmapped /login)
        mockMvc.perform(post("/logout")
                .with(csrf())
                .header(HttpHeaders.ACCEPT, org.springframework.http.MediaType.TEXT_HTML_VALUE))
            .andExpect(status().is3xxRedirection())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .redirectedUrl("/app/reports"));
    }

    @Test
    @DisplayName("should return 401 for Basic login of a blocked user")
    void basicLogin_blockedUser_rejected() throws Exception {
        // GIVEN — a blocked user with a valid password hash, requireApiAuth on so /api enforces
        final String blockedName = "blockedbob";
        final String blockedPass = "BlockedPass1";
        userRepository.findByUsername(blockedName).ifPresent(userRepository::delete);
        userRepository.save(UserEntity.builder()
            .id(UUID.randomUUID())
            .username(blockedName)
            .displayName(blockedName)
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordHash(passwordEncoder.encode(blockedPass))
            .passwordTemporary(false)
            .blocked(true)
            .mainAdmin(false)
            .build());
        systemSettingsService.updateRequireApiAuth(true, "test-actor");
        try {
            // WHEN — Basic login of the blocked user against a protected endpoint
            // THEN — 401 (account locked → authentication fails)
            mockMvc.perform(get(API_REPORT_PATH)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(blockedName, blockedPass)))
                .andExpect(status().isUnauthorized());
        } finally {
            systemSettingsService.updateRequireApiAuth(false, "test-actor");
        }
    }

    @Test
    @DisplayName("should return 401 for Basic login of the guest user (no local password)")
    void basicLogin_guestUser_rejected() throws Exception {
        // GIVEN — guest is seeded with a null password hash, requireApiAuth on
        systemSettingsService.updateRequireApiAuth(true, "test-actor");
        try {
            // WHEN — Basic login attempt as guest (any password) against a protected endpoint
            // THEN — 401 (no local password → UsernameNotFoundException → auth fails)
            mockMvc.perform(get(API_REPORT_PATH)
                    .header(HttpHeaders.AUTHORIZATION,
                        basicAuthHeader(CurrentUserProvider.GUEST_USERNAME, "anything")))
                .andExpect(status().isUnauthorized());
        } finally {
            systemSettingsService.updateRequireApiAuth(false, "test-actor");
        }
    }

    ///// helpers /////

    private void clearTemporaryPassword(String username) {
        final UserEntity user = userRepository.findByUsername(username).orElseThrow();
        user.setPasswordTemporary(false);
        userRepository.save(user);
    }

    private void seedUser(String username, String plainPassword, UserRole role) {
        userRepository.findByUsername(username).ifPresent(userRepository::delete);
        userRepository.save(UserEntity.builder()
            .id(UUID.randomUUID())
            .username(username)
            .displayName(username)
            .role(role)
            .createdAt(Instant.now())
            .passwordHash(passwordEncoder.encode(plainPassword))
            .passwordTemporary(false)
            .blocked(false)
            .mainAdmin(false)
            .build());
    }

    private static String basicAuthHeader(String username, String password) {
        final String credentials = username + ":" + password;
        final String encoded = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
