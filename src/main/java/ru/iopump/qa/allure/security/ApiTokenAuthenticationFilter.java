package ru.iopump.qa.allure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.service.ApiTokenService;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Servlet filter that authenticates requests carrying an {@value #HEADER_NAME} header.
 * <p>
 * Header contract (standard-style, mirrors GitLab {@code PRIVATE-TOKEN} / GitHub PAT):
 * <ul>
 *   <li>Header name: {@value #HEADER_NAME}</li>
 *   <li>Header value: raw project token (prefix {@value ApiTokenService#TOKEN_PREFIX}), NO {@code Bearer } prefix</li>
 * </ul>
 * Semantics:
 * <ul>
 *   <li>Header absent → pass through unchanged (Basic / OAuth2 / anonymous still apply).</li>
 *   <li>Header present, token valid → populate {@link SecurityContextHolder} and continue the chain.</li>
 *   <li>Header present, token invalid / expired / revoked → explicit {@code 401 Unauthorized}
 *       with {@code WWW-Authenticate: X-API-Token realm="api"}. Never falls through to Basic
 *       — a caller who sent a token means "authenticate me by token".</li>
 * </ul>
 * The standard {@code Authorization} header is never inspected by this filter, so Basic auth
 * continues to own its own header.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-API-Token";
    public static final String REALM = "api";
    private static final String WWW_AUTHENTICATE_VALUE = HEADER_NAME + " realm=\"" + REALM + "\"";

    private final ApiTokenService apiTokenService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String token = request.getHeader(HEADER_NAME);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        final String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            reject(response, request.getRequestURI(), "empty token");
            return;
        }
        final Optional<UserEntity> authenticated = apiTokenService.authenticate(trimmed);
        if (authenticated.isEmpty()) {
            reject(response, request.getRequestURI(), "invalid/expired/revoked token");
            return;
        }
        final UserEntity user = authenticated.get();
        final var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        // The principal is a Spring UserDetails (not the raw JPA entity) so that
        // AbstractAuthenticationToken.getName() returns the username — consumers like
        // ForcePasswordChangeFilter and CurrentUserProvider resolve the row uniformly
        // by name for both Basic and token auth. A mutable persistence entity must never
        // leak into the SecurityContext for the lifetime of the request.
        final UserDetails principal = User.withUsername(user.getUsername())
            .password("N/A")
            .authorities(authorities)
            .build();
        final var authentication = new UsernamePasswordAuthenticationToken(principal, "N/A", authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated '{}' via {} on {}", user.getUsername(), HEADER_NAME, request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, String uri, String reason) throws IOException {
        log.debug("Rejected {} on {} ({})", HEADER_NAME, uri, reason);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_VALUE);
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API token");
    }
}
