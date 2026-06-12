package ru.iopump.qa.allure.web;

import gg.jte.springframework.boot.autoconfigure.JteAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.iopump.qa.allure.config.RedirectConfiguration;
import ru.iopump.qa.allure.config.WebConfiguration;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.properties.BasicProperties;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.ApiTokenService;
import ru.iopump.qa.allure.service.TokenLimitExceededException;
import ru.iopump.qa.allure.service.TokenPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    value = ProfileWebController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({WebExceptionAdvice.class, WebConfiguration.class, RedirectConfiguration.class,
    JteAutoConfiguration.class, GlobalModelAdvice.class})
@EnableConfigurationProperties({AllureProperties.class, BasicProperties.class})
class ProfileWebControllerTest {

    private static final String PROFILE_PATH = "/app/profile";
    private static final String TOKENS_PATH = "/app/profile/tokens";
    private static final String TOKEN_NAME = "ci-pipeline";
    private static final String PLAIN_TOKEN_VALUE = "bqa_generatedPlainValue1234567890";
    private static final String FLASH_KEY = "flash";
    private static final String FLASH_JUST_CREATED = "justCreatedToken";
    private static final String FLASH_LEVEL_KEY = "level";
    private static final String LEVEL_SUCCESS = "success";
    private static final String LEVEL_ERROR = "error";
    private static final String SIGN_IN_PROMPT = "Sign in to manage API tokens";
    private static final String ACTIVE_TOKENS_LABEL = "Active tokens";
    private static final String NEW_TOKEN_LABEL = "+ New token";
    private static final String TOKEN_LIMIT_REACHED = "Token limit reached";
    private static final String USER_LIMIT_COUNT = "10 of 10";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiTokenService apiTokenService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private TokenPolicy tokenPolicy;

    private static final int GUEST_LIMIT = 5;
    private static final int USER_LIMIT = 10;

    private UserEntity authenticatedUser;
    private UserEntity guestUser;
    private UserEntity seededGuest;

    @BeforeEach
    void setUp() {
        authenticatedUser = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("alice")
            .displayName("Alice")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .build();
        guestUser = UserEntity.builder()
            .id(null)
            .username("guest")
            .displayName("Guest")
            .role(UserRole.GUEST)
            .createdAt(Instant.EPOCH)
            .build();
        seededGuest = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("guest")
            .displayName("Guest")
            .role(UserRole.GUEST)
            .createdAt(Instant.EPOCH)
            .build();
        org.mockito.Mockito.lenient().when(tokenPolicy.maxActiveTokens(UserRole.USER)).thenReturn(USER_LIMIT);
        org.mockito.Mockito.lenient().when(tokenPolicy.maxActiveTokens(UserRole.GUEST)).thenReturn(GUEST_LIMIT);
        org.mockito.Mockito.lenient().when(tokenPolicy.maxActiveTokens(UserRole.ADMIN)).thenReturn(50);
    }

    @Test
    @DisplayName("should render profile page with 200 when GET /app/profile as authenticated user")
    void indexAuthenticatedRenders() throws Exception {
        // GIVEN — an authenticated user with no tokens
        when(currentUserProvider.current()).thenReturn(authenticatedUser);
        when(apiTokenService.listAll(authenticatedUser)).thenReturn(Collections.emptyList());

        // WHEN — GET /app/profile
        MvcResult result = mockMvc.perform(get(PROFILE_PATH))
            .andExpect(status().isOk())
            .andReturn();

        // THEN — response body contains the display name in the avatar title
        final String body = result.getResponse().getContentAsString();
        assertThat(body)
            .as("profile page must embed the authenticated user's display name")
            .contains("Alice");
    }

    @Test
    @DisplayName("should render profile page for guest when GET /app/profile without authentication")
    void indexGuestRenders() throws Exception {
        // GIVEN — guest fallback
        when(currentUserProvider.current()).thenReturn(guestUser);

        // WHEN — GET /app/profile
        MvcResult result = mockMvc.perform(get(PROFILE_PATH))
            .andExpect(status().isOk())
            .andReturn();

        // THEN — body announces guest mode
        final String body = result.getResponse().getContentAsString();
        assertThat(body)
            .as("guest profile page must contain the guest messaging")
            .contains("browsing as a guest");
    }

    @Test
    @DisplayName("should redirect to /app/profile with success flash and justCreatedToken when POST /tokens valid form")
    void createTokenHappyPath() throws Exception {
        // GIVEN — authenticated user and service issuing a fresh token
        when(currentUserProvider.current()).thenReturn(authenticatedUser);
        final UUID tokenId = UUID.randomUUID();
        when(apiTokenService.createToken(eq(authenticatedUser), eq(TOKEN_NAME), any(Duration.class)))
            .thenReturn(new ApiTokenService.TokenIssueResult(tokenId, PLAIN_TOKEN_VALUE));

        // WHEN — POST the create-token form
        MvcResult result = mockMvc.perform(post(TOKENS_PATH)
                .param("name", TOKEN_NAME)
                .param("expiration", "DAYS_30"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PROFILE_PATH))
            .andReturn();

        // THEN — flash carries success level and justCreatedToken with the plain value
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("successful token creation must redirect with 'success' flash level")
            .isEqualTo(LEVEL_SUCCESS);
        final Map<?, ?> justCreated = extractFlashMap(result, FLASH_JUST_CREATED);
        assertThat(justCreated.get("plain"))
            .as("justCreatedToken flash must carry the plain token value exactly once")
            .isEqualTo(PLAIN_TOKEN_VALUE);
        assertThat(justCreated.get("id"))
            .as("justCreatedToken flash must carry the new token's id")
            .isEqualTo(tokenId.toString());
        verify(apiTokenService).createToken(eq(authenticatedUser), eq(TOKEN_NAME), any(Duration.class));
    }

    @Test
    @DisplayName("should respond 403 when POST /tokens as anonymous fallback guest (no persisted id)")
    void createTokenAsAnonymousFallbackForbidden() throws Exception {
        // GIVEN — anonymous fallback guest with null id
        when(currentUserProvider.current()).thenReturn(guestUser);

        // WHEN — POST the create-token form
        mockMvc.perform(post(TOKENS_PATH)
                .param("name", TOKEN_NAME)
                .param("expiration", "DAYS_30"))
            // THEN — 403 Forbidden
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should respond 403 when POST /tokens as persisted guest (read-only)")
    void createTokenAsPersistedGuestForbidden() throws Exception {
        // GIVEN — seeded (persisted) guest with role GUEST and a real id
        when(currentUserProvider.current()).thenReturn(seededGuest);

        // WHEN — POST the create-token form
        mockMvc.perform(post(TOKENS_PATH)
                .param("name", TOKEN_NAME)
                .param("expiration", "DAYS_30"))
            // THEN — 403 Forbidden, blocked before reaching the service
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should redirect with success flash when POST /tokens/{id} with _method=delete revokes an active token")
    void revokeTokenHappyPath() throws Exception {
        // GIVEN — authenticated user and a revoke that succeeds
        when(currentUserProvider.current()).thenReturn(authenticatedUser);
        final UUID tokenId = UUID.randomUUID();
        when(apiTokenService.revoke(authenticatedUser, tokenId)).thenReturn(true);

        // WHEN — POST with _method=delete (Spring's HiddenHttpMethodFilter rewrites to DELETE)
        MvcResult result = mockMvc.perform(post(TOKENS_PATH + "/" + tokenId)
                .param("_method", "delete"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PROFILE_PATH))
            .andReturn();

        // THEN — success flash
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("successful revoke must set 'success' flash level")
            .isEqualTo(LEVEL_SUCCESS);
        verify(apiTokenService).revoke(authenticatedUser, tokenId);
    }

    @Test
    @DisplayName("should redirect with error flash when revoke returns false for unknown token")
    void revokeTokenUnknown() throws Exception {
        // GIVEN — revoke returns false (token not found for this owner)
        when(currentUserProvider.current()).thenReturn(authenticatedUser);
        final UUID tokenId = UUID.randomUUID();
        when(apiTokenService.revoke(authenticatedUser, tokenId)).thenReturn(false);

        // WHEN — POST with _method=delete
        MvcResult result = mockMvc.perform(post(TOKENS_PATH + "/" + tokenId)
                .param("_method", "delete"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        // THEN — error flash
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("failed revoke must set 'error' flash level")
            .isEqualTo(LEVEL_ERROR);
    }

    @Test
    @DisplayName("should redirect with error flash when service throws TokenLimitExceededException on POST /tokens")
    void createTokenWhenLimitExceededRedirectsWithError() throws Exception {
        // GIVEN — authenticated USER at the 10-token cap; service raises the domain exception
        when(currentUserProvider.current()).thenReturn(authenticatedUser);
        when(apiTokenService.createToken(eq(authenticatedUser), eq(TOKEN_NAME), any(Duration.class)))
            .thenThrow(new TokenLimitExceededException(UserRole.USER, USER_LIMIT, USER_LIMIT));

        // WHEN — attempt to create a token beyond the cap
        MvcResult result = mockMvc.perform(post(TOKENS_PATH)
                .param("name", TOKEN_NAME)
                .param("expiration", "DAYS_30"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PROFILE_PATH))
            .andReturn();

        // THEN — error flash carries the "Token limit reached" message
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("limit-exceeded redirect must set 'error' flash level")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get("message"))
            .as("flash message must surface the 'Token limit reached' text")
            .asString()
            .contains(TOKEN_LIMIT_REACHED)
            .contains(USER_LIMIT_COUNT);
    }

    @Test
    @DisplayName("should render read-only profile without token controls when current user is a seeded guest")
    void indexWhenSeededGuestRendersReadOnlyProfile() throws Exception {
        // GIVEN — seeded (persisted) guest with a real id
        when(currentUserProvider.current()).thenReturn(seededGuest);

        // WHEN — GET /app/profile
        MvcResult result = mockMvc.perform(get(PROFILE_PATH))
            .andExpect(status().isOk())
            .andReturn();

        // THEN — body shows the read-only sign-in prompt and no token-management controls
        final String body = result.getResponse().getContentAsString();
        assertThat(body)
            .as("seeded-guest profile must show the read-only sign-in prompt")
            .contains(SIGN_IN_PROMPT);
        assertThat(body)
            .as("seeded-guest profile must not expose token-management controls")
            .doesNotContain(ACTIVE_TOKENS_LABEL)
            .doesNotContain(NEW_TOKEN_LABEL);
    }

    ///// helpers /////

    private static Map<?, ?> extractFlashMap(MvcResult result, String key) {
        Object flashValue = result.getFlashMap().get(key);
        assertThat(flashValue)
            .as("flash attribute under key '%s' must be a Map", key)
            .isInstanceOf(Map.class);
        return (Map<?, ?>) flashValue;
    }
}
