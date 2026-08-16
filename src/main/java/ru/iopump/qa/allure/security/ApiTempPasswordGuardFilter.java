package ru.iopump.qa.allure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.repo.UserRepository;

import java.io.IOException;

/**
 * Blocks a still-temporary (admin-issued / default) password from authenticating against the
 * stateless {@code /api/**} write/API surface before it has been rotated.
 * <p>
 * {@link ForcePasswordChangeFilter} only redirects on the browser surface ({@code /app/**}); on
 * its own that leaves the publicly-known default {@code admin/admin} credential usable via Basic
 * auth on the API (finding C1-5). This filter closes that gap by rejecting, with
 * {@code 403 Forbidden}, any authenticated non-anonymous principal whose backing
 * {@link UserEntity#isPasswordTemporary()} flag is still set.
 * <p>
 * Scope decisions:
 * <ul>
 *   <li>Only {@code /api/**} is guarded — that is the surface finding C1-5 was about. The
 *       read-only {@code /allure/**} report content is already gated by the API authorization
 *       manager and is deliberately left out: guarding it would make an authenticated user with an
 *       unrotated temp password stricter than an anonymous reader on the same URL, with no
 *       forced-rotation redirect to recover. The {@code /app} rotation flow
 *       ({@code /app/profile/password}) must also stay reachable so the user can set a new
 *       password.</li>
 *   <li>Requests carrying an {@value ApiTokenAuthenticationFilter#HEADER_NAME} header are exempt:
 *       a token is a deliberately-minted, secret, individually-revocable credential (not a
 *       publicly-known default), and minting one already requires passing the browser rotation
 *       gate. The exposure being closed here is specifically Basic/session use of a temp password.</li>
 *   <li>Runs after the authentication filters (added {@code after AuthorizationFilter}) so the
 *       resolved principal is available; anonymous and unauthenticated traffic pass through
 *       untouched.</li>
 * </ul>
 */
@Slf4j
public class ApiTempPasswordGuardFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";

    private final UserRepository userRepository;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    public ApiTempPasswordGuardFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
        throws ServletException, IOException {
        final String path = relativePath(request);
        if (!path.startsWith(API_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        // A deliberately-minted API token is exempt (see class javadoc): only Basic/session
        // logins with an unrotated temporary password are blocked here.
        if (request.getHeader(ApiTokenAuthenticationFilter.HEADER_NAME) != null) {
            filterChain.doFilter(request, response);
            return;
        }
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || trustResolver.isAnonymous(authentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        final String principalName = authentication.getName();
        if (principalName == null || principalName.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        final UserEntity user = userRepository.findByUsername(principalName).orElse(null);
        if (user != null && user.isPasswordTemporary()) {
            log.debug("Blocking temporary-password principal '{}' on stateless surface {}", principalName, path);
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Password rotation required before API access — rotate your temporary password via the web UI.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * @return the request path relative to the servlet context path so the {@code /api/**} prefix
     * check behaves identically under any configured {@code server.servlet.context-path}
     * (mirrors {@link ForcePasswordChangeFilter}).
     */
    private static String relativePath(HttpServletRequest request) {
        final String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        final String uri = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
