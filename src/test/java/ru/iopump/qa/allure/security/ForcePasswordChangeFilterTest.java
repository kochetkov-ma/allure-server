package ru.iopump.qa.allure.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit test for {@link ForcePasswordChangeFilter} focused on context-path-relative
 * matching (R2-4). Before the fix the filter matched the whitelist and the
 * {@code /app/**} prefix against the raw {@code request.getRequestURI()}, which under a
 * non-root {@code server.servlet.context-path} (e.g. {@code /server}) silently disabled
 * the forced-password-change flow and produced a context-path-less redirect.
 */
@ExtendWith(MockitoExtension.class)
class ForcePasswordChangeFilterTest {

    private static final String TEMP_USER = "tempuser";
    private static final String CONTEXT_PATH = "/server";

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should redirect a temp-password user under a non-root context-path with the context-path-prefixed target")
    void redirectsUnderContextPath() throws Exception {
        // GIVEN — a temp-password user authenticated, request to /app/reports under context-path /server
        authenticate(TEMP_USER);
        lenient().when(userRepository.findByUsername(TEMP_USER))
            .thenReturn(Optional.of(tempPasswordUser()));
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", CONTEXT_PATH + "/app/reports");
        request.setContextPath(CONTEXT_PATH);
        request.setServletPath("/app/reports");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        // WHEN — the filter runs
        new ForcePasswordChangeFilter(userRepository).doFilter(request, response, filterChain);

        // THEN — redirected to the context-path-prefixed forced password page, chain not continued
        assertThat(response.getRedirectedUrl())
            .as("redirect must include the servlet context path so it resolves under /server")
            .isEqualTo(CONTEXT_PATH + ForcePasswordChangeFilter.REDIRECT_WITH_FLAG);
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("should NOT redirect a temp-password user already on the password page under a non-root context-path")
    void doesNotRedirectOnPasswordPageUnderContextPath() throws Exception {
        // GIVEN — temp-password user requesting the change-password page itself under context-path /server
        authenticate(TEMP_USER);
        final MockHttpServletRequest request =
            new MockHttpServletRequest("GET", CONTEXT_PATH + ForcePasswordChangeFilter.TARGET);
        request.setContextPath(CONTEXT_PATH);
        request.setServletPath(ForcePasswordChangeFilter.TARGET);
        final MockHttpServletResponse response = new MockHttpServletResponse();

        // WHEN — the filter runs
        new ForcePasswordChangeFilter(userRepository).doFilter(request, response, filterChain);

        // THEN — whitelisted: no redirect, the chain continues (no infinite loop)
        assertThat(response.getRedirectedUrl())
            .as("password page must be whitelisted under a context-path (loop prevention)")
            .isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should redirect a temp-password user with the bare target when no context-path is configured")
    void redirectsAtRootContext() throws Exception {
        // GIVEN — temp-password user, request to /app/reports with empty (root) context-path
        authenticate(TEMP_USER);
        lenient().when(userRepository.findByUsername(TEMP_USER))
            .thenReturn(Optional.of(tempPasswordUser()));
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/reports");
        request.setContextPath("");
        request.setServletPath("/app/reports");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        // WHEN — the filter runs
        new ForcePasswordChangeFilter(userRepository).doFilter(request, response, filterChain);

        // THEN — redirected to the bare forced target (default deployment unchanged)
        assertThat(response.getRedirectedUrl())
            .as("root-context redirect must remain the bare forced target")
            .isEqualTo(ForcePasswordChangeFilter.REDIRECT_WITH_FLAG);
        verifyNoInteractions(filterChain);
    }

    private static void authenticate(String username) {
        final UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
            username, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private static UserEntity tempPasswordUser() {
        return UserEntity.builder()
            .id(UUID.randomUUID())
            .username(TEMP_USER)
            .displayName("Temp User")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordHash("$2a$hash")
            .passwordTemporary(true)
            .blocked(false)
            .mainAdmin(false)
            .build();
    }
}
