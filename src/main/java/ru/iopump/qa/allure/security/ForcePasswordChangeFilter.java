package ru.iopump.qa.allure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.repo.UserRepository;

import java.io.IOException;
import java.util.Set;

/**
 * Redirects authenticated users whose {@code passwordTemporary} flag is still set
 * to {@code /app/profile/password?forced=true} on every request under
 * {@code /app/**} — except the password-change endpoint itself and logout.
 * <p>
 * Applies only to browser traffic ({@code /app/**}); the API surface
 * ({@code /api/**}) is unaffected because password rotation is a UI concern.
 */
@RequiredArgsConstructor
@Slf4j
public class ForcePasswordChangeFilter extends OncePerRequestFilter {

    public static final String TARGET = "/app/profile/password";
    public static final String REDIRECT_WITH_FLAG = TARGET + "?forced=true";
    private static final String APP_PREFIX = "/app/";
    private static final Set<String> WHITELIST = Set.of(
        TARGET,
        "/logout"
    );

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
        throws ServletException, IOException {
        // Match against the context-path-relative path so a non-root
        // server.servlet.context-path (e.g. "/server") does not silently disable the
        // forced flow: getRequestURI() includes the context path, getServletPath() does not.
        final String path = relativePath(request);
        if (!path.startsWith(APP_PREFIX) || WHITELIST.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        final String principalName = authentication.getName();
        if (principalName == null || principalName.isBlank() || "anonymousUser".equals(principalName)) {
            filterChain.doFilter(request, response);
            return;
        }
        final UserEntity user = userRepository.findByUsername(principalName).orElse(null);
        if (user != null && user.isPasswordTemporary()) {
            log.debug("Forcing password change for '{}' (requested {})", principalName, path);
            // Prefix with the context path so the redirect resolves correctly under a
            // non-root server.servlet.context-path.
            response.sendRedirect(request.getContextPath() + REDIRECT_WITH_FLAG);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * @return the request path relative to the servlet context path, normalised so the
     * {@code /app/**} prefix and whitelist checks behave identically regardless of any
     * configured {@code server.servlet.context-path}.
     */
    private static String relativePath(HttpServletRequest request) {
        final String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        // Fallback when the servlet container does not populate servletPath: strip the
        // context path from the raw request URI manually.
        final String uri = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
