package ru.iopump.qa.allure.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.service.ApiTokenService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTokenAuthenticationFilterTest {

    private static final String PLAIN_TOKEN = "bqa_example1234567890";
    private static final String EXPECTED_ROLE_AUTHORITY = "ROLE_USER";
    private static final String EXPECTED_WWW_AUTHENTICATE = "X-API-Token realm=\"api\"";

    @Mock
    private ApiTokenService apiTokenService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ApiTokenAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should set SecurityContext authentication when X-API-Token header carries a valid token")
    void validTokenAuthenticates() throws Exception {
        // GIVEN — service recognises the token and returns the owning user
        final UserEntity user = userWithRole(UserRole.USER);
        when(apiTokenService.authenticate(PLAIN_TOKEN)).thenReturn(Optional.of(user));
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/report");
        request.addHeader(ApiTokenAuthenticationFilter.HEADER_NAME, PLAIN_TOKEN);
        final MockHttpServletResponse response = new MockHttpServletResponse();

        // WHEN — filter processes the request
        filter.doFilter(request, response, filterChain);

        // THEN — SecurityContext authenticated, chain invoked, no 401 written
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication)
            .as("a valid X-API-Token must populate the SecurityContext")
            .isNotNull();
        assertThat(authentication.getPrincipal())
            .as("principal must be a Spring UserDetails, not the raw JPA entity")
            .isInstanceOf(org.springframework.security.core.userdetails.UserDetails.class);
        assertThat(((org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal())
                .getUsername())
            .as("principal username must be the entity username")
            .isEqualTo("alice");
        assertThat(authentication.getName())
            .as("getName() must return the username (not the entity toString) so consumers resolve uniformly")
            .isEqualTo("alice");
        assertThat(authentication.getAuthorities())
            .as("authorities must contain a ROLE_<role> granted authority")
            .extracting(Object::toString)
            .containsExactly(EXPECTED_ROLE_AUTHORITY);
        assertThat(response.getStatus())
            .as("valid token must not set a 401 status")
            .isEqualTo(HttpStatus.OK.value());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should pass through untouched when X-API-Token header is missing")
    void noTokenHeaderPassThrough() throws Exception {
        // GIVEN — no X-API-Token header on the request
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/report");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        // WHEN — filter processes the request
        filter.doFilter(request, response, filterChain);

        // THEN — chain invoked, no authentication set, service never called, no 401
        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .as("filter must not authenticate when the header is missing")
            .isNull();
        assertThat(response.getStatus())
            .as("missing header must not trigger a 401 from this filter")
            .isEqualTo(HttpStatus.OK.value());
        verify(filterChain).doFilter(request, response);
        verify(apiTokenService, never()).authenticate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should ignore Authorization: Bearer bqa_... and pass through (Bearer no longer accepted)")
    void legacyBearerHeaderIgnored() throws Exception {
        // GIVEN — a request that uses the OLD Bearer contract, with no X-API-Token header
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/report");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + PLAIN_TOKEN);
        final MockHttpServletResponse response = new MockHttpServletResponse();

        // WHEN — filter processes the request
        filter.doFilter(request, response, filterChain);

        // THEN — filter is blind to Authorization; chain invoked, service never called
        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .as("legacy Bearer header must be ignored by this filter")
            .isNull();
        assertThat(response.getStatus())
            .as("legacy Bearer header must not trigger a 401 from this filter")
            .isEqualTo(HttpStatus.OK.value());
        verify(filterChain).doFilter(request, response);
        verify(apiTokenService, never()).authenticate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should reject with 401 and WWW-Authenticate when X-API-Token is invalid/expired/revoked")
    void invalidTokenReturnsUnauthorized() throws Exception {
        // GIVEN — present X-API-Token header that the service rejects
        when(apiTokenService.authenticate(PLAIN_TOKEN)).thenReturn(Optional.empty());
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/report");
        request.addHeader(ApiTokenAuthenticationFilter.HEADER_NAME, PLAIN_TOKEN);
        final MockHttpServletResponse response = new MockHttpServletResponse();

        // WHEN — filter processes the request
        filter.doFilter(request, response, filterChain);

        // THEN — 401 with WWW-Authenticate challenge, SecurityContext empty, chain NOT invoked
        assertThat(response.getStatus())
            .as("invalid token must produce a 401 Unauthorized")
            .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
            .as("401 must carry the X-API-Token WWW-Authenticate challenge")
            .isEqualTo(EXPECTED_WWW_AUTHENTICATE);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .as("invalid token must not populate the SecurityContext")
            .isNull();
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("should reject with 401 when X-API-Token is present but blank")
    void blankTokenReturnsUnauthorized() throws Exception {
        // GIVEN — X-API-Token header set to whitespace only
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/report");
        request.addHeader(ApiTokenAuthenticationFilter.HEADER_NAME, "   ");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        // WHEN — filter processes the request
        filter.doFilter(request, response, filterChain);

        // THEN — 401 with challenge, service never called, chain NOT invoked
        assertThat(response.getStatus())
            .as("blank token must produce a 401 Unauthorized")
            .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
            .as("401 must carry the X-API-Token WWW-Authenticate challenge")
            .isEqualTo(EXPECTED_WWW_AUTHENTICATE);
        verify(filterChain, never()).doFilter(request, response);
        verify(apiTokenService, never()).authenticate(org.mockito.ArgumentMatchers.any());
    }

    private static UserEntity userWithRole(UserRole role) {
        return UserEntity.builder()
            .id(UUID.randomUUID())
            .username("alice")
            .displayName("Alice")
            .role(role)
            .createdAt(Instant.now())
            .build();
    }
}
