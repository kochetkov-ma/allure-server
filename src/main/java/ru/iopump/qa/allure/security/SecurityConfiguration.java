package ru.iopump.qa.allure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import ru.iopump.qa.allure.config.WebConfiguration;
import ru.iopump.qa.allure.entity.SystemSettingsEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.properties.AppSecurityProperties;
import ru.iopump.qa.allure.properties.BasicProperties;
import ru.iopump.qa.allure.repo.SystemSettingsRepository;
import ru.iopump.qa.allure.repo.UserRepository;
import ru.iopump.qa.allure.service.SystemSettingsService;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Security wiring. Auth is always on; anonymous traffic is accepted for read-only
 * {@code /app/**} GET traffic (guest read-only UI) and for {@code /api/**} and
 * {@code /allure/**} while the runtime flag {@code requireApiAuth} is {@code false}.
 * Both {@code /api/**} and {@code /allure/**} share the same {@link #apiAuthorizationManager()}
 * rule, so toggling {@code requireApiAuth} to {@code true} re-gates report content as well.
 * <p>
 * Mutations are protected at the matcher level: every POST/DELETE under
 * {@code /app/reports/**} and {@code /app/results/**}, the token-minting
 * {@code POST /app/profile/tokens}, and the self-service
 * {@code POST /app/profile/password}, require a non-anonymous, non-{@code GUEST}
 * principal (see {@link #mutationAuthorizationManager()}). Admin-only paths
 * ({@code /app/admin/**}) are additionally protected by
 * {@code @PreAuthorize("hasRole('ADMIN')")} on the relevant controllers.
 * <p>
 * CSRF protection is enabled for the browser surface (any state-changing {@code /app/**}
 * request and {@code /logout}) via a JS-readable {@link CookieCsrfTokenRepository}; templates
 * emit the token as a hidden form field and a {@code <meta>} tag consumed by HTMX. The
 * stateless {@code /api/**} and {@code /allure/**} surfaces are exempt
 * ({@code ignoringRequestMatchers}) so token/Basic clients need not carry a CSRF token.
 * <p>
 * A temporary (admin-issued / default) password must be rotated before it can authenticate
 * against the stateless surfaces: {@link ApiTempPasswordGuardFilter} rejects such principals on
 * {@code /api/**} and {@code /allure/**} (Basic/session logins), while the {@code /app} rotation
 * flow stays reachable so the user can set a new password.
 * <p>
 * Legacy {@code basic.auth.enable} is honored for backward compatibility: when set
 * to {@code true} every request that is not an unauthenticated static asset
 * ({@code /css|/js|/img}, {@code /favicon.ico}) requires authentication. That
 * specifically forces {@code /api/**} (writes) and {@code /allure/**} (report content)
 * to authenticated() unconditionally, regardless of the {@code requireApiAuth} toggle,
 * so deployments that relied on the flag for protection are not silently opened up on
 * upgrade. Static assets stay public so the login page can render. The flag is
 * deprecated; operators should migrate to the runtime {@code requireApiAuth} toggle
 * (admin settings UI).
 * Basic auth against the database is always available via {@link DbUserDetailsService}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfiguration {

    private final ApiTokenAuthenticationFilter apiTokenFilter;
    private final UserRepository userRepository;
    private final DbUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final SystemSettingsService systemSettingsService;
    private final SystemSettingsRepository systemSettingsRepository;
    private final boolean enableOAuth2;
    private final boolean legacyBasicAuthEnabled;
    private final boolean apiAuthBootstrapDefault;

    public SecurityConfiguration(ApiTokenAuthenticationFilter apiTokenFilter,
                                 UserRepository userRepository,
                                 DbUserDetailsService userDetailsService,
                                 PasswordEncoder passwordEncoder,
                                 SystemSettingsService systemSettingsService,
                                 SystemSettingsRepository systemSettingsRepository,
                                 BasicProperties basicProperties,
                                 AppSecurityProperties appSecurityProperties) {
        this.apiTokenFilter = apiTokenFilter;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.systemSettingsService = systemSettingsService;
        this.systemSettingsRepository = systemSettingsRepository;
        this.enableOAuth2 = appSecurityProperties.enableOauth2();
        this.legacyBasicAuthEnabled = basicProperties.enable();
        this.apiAuthBootstrapDefault = appSecurityProperties.requireApiAuth();

        if (legacyBasicAuthEnabled) {
            log.warn("[ALLURE SERVER SECURITY] 'basic.auth.enable=true' is DEPRECATED. For backward "
                + "compatibility every request except public static assets (css/js/img, favicon) "
                + "now requires authentication — including /api/** and /allure/** (legacy behavior). "
                + "Migrate to the runtime 'require API auth' toggle in /app/admin/settings and remove "
                + "this property.");
        }
        log.info("[ALLURE SERVER SECURITY] Always-on auth | OAuth2: {} | legacy basic.auth.enable: {} "
                + "| API auth bootstrap default: {}",
            enableOAuth2, legacyBasicAuthEnabled, appSecurityProperties.requireApiAuth());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        final AuthorizationManager<RequestAuthorizationContext> apiAuthorizationManager = apiAuthorizationManager();
        final AuthorizationManager<RequestAuthorizationContext> mutationAuthorizationManager =
            mutationAuthorizationManager();

        http
            .headers(it -> it.frameOptions(FrameOptionsConfig::sameOrigin))
            // CSRF is enforced for the browser surface (state-changing /app/** forms + /logout):
            // the UI is served over cookies while HiddenHttpMethodFilter is on, so mutations would
            // otherwise be forgeable cross-site. The token repository is a JS-readable cookie so
            // HTMX can echo it via a meta tag; templates also embed it as a hidden form field.
            // The stateless /api/** and /allure/** surfaces are exempt so token/Basic clients
            // (CI pipelines, Allure plugins) are unaffected and carry no CSRF token.
            .csrf(it -> it
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/**", "/allure/**"))
            // API-token filter runs before the Basic-auth filter so a valid X-API-Token header
            // short-circuits username/password evaluation.
            .addFilterBefore(apiTokenFilter, UsernamePasswordAuthenticationFilter.class)
            // Force-password-change runs AFTER authorization so only the resolved authenticated
            // principal is inspected. Unauthenticated requests are never redirected.
            .addFilterAfter(new ForcePasswordChangeFilter(userRepository), AuthorizationFilter.class)
            // Same ordering rationale as above: block a temporary/default password on the
            // stateless /api/** and /allure/** surfaces (Basic/session) until it is rotated. A
            // deliberately-minted API token is exempt (see the filter). /app/** is untouched so
            // the rotation flow stays reachable.
            .addFilterAfter(new ApiTempPasswordGuardFilter(userRepository), AuthorizationFilter.class)
            .authorizeHttpRequests(it -> {
                // Static assets are always public so the login page can render even in
                // legacy lock-everything mode. /swagger/** and /icon.svg are Swagger-UI and
                // app branding assets (referenced by layout/main.jte and SwaggerBrandingFilter)
                // and must load pre-auth in BOTH modes — they are public by default already,
                // but in legacy mode they would otherwise be gated to authenticated().
                it.requestMatchers(WebConfiguration.CSS_PATH_PATTERN,
                        WebConfiguration.JS_PATH_PATTERN,
                        WebConfiguration.IMG_PATH_PATTERN,
                        "/swagger/**",
                        "/icon.svg",
                        "/favicon.ico").permitAll();
                // Actuator liveness/readiness: /actuator/health (and its /liveness, /readiness
                // groups) must be reachable pre-auth in BOTH modes so the Docker HEALTHCHECK
                // works even under legacy 'basic.auth.enable=true' (where anyRequest() would
                // otherwise gate it to authenticated() → 401). Only health is exposed here; no
                // other actuator endpoint is opened. Registered before the legacy vs non-legacy
                // branch so first-match-wins makes it public in both. GET-only, so no CSRF concern.
                it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                if (legacyBasicAuthEnabled) {
                    // Backward-compat: pre-branch behavior required authentication for the
                    // whole surface. Because authorizeHttpRequests is first-match-wins, the
                    // /api/** and /allure/** matchers MUST be gated to authenticated() here —
                    // otherwise the later anyRequest() rule would never apply to them and they
                    // would stay anonymously open, re-opening the very gap this flag closes.
                    it.requestMatchers("/allure/**").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/app/signin").authenticated()
                        .anyRequest().authenticated();
                } else {
                    // /allure/** (generated report content) is gated by the SAME runtime
                    // toggle as /api/**: when requireApiAuth is true, anonymous report reads
                    // are blocked (401); when false, the guest read-only fallback still serves
                    // them. Without this, report content stayed anonymously open regardless of
                    // the toggle (F9).
                    it.requestMatchers("/allure/**").access(apiAuthorizationManager)
                        .requestMatchers("/api/**").access(apiAuthorizationManager)
                        .requestMatchers("/app/signin").authenticated();
                    // Mutations require an authenticated, non-guest principal even though
                    // CSRF is disabled — these matcher rules are what guard write paths.
                    it.requestMatchers(HttpMethod.POST, "/app/reports/**", "/app/results/**")
                            .access(mutationAuthorizationManager)
                        .requestMatchers(HttpMethod.DELETE, "/app/reports/**", "/app/results/**")
                            .access(mutationAuthorizationManager)
                        .requestMatchers(HttpMethod.POST, "/app/profile/tokens", "/app/profile/tokens/**")
                            .access(mutationAuthorizationManager)
                        .requestMatchers(HttpMethod.DELETE, "/app/profile/tokens/**")
                            .access(mutationAuthorizationManager)
                        // Self-service password change is a mutation: reject anonymous and
                        // ROLE_GUEST principals uniformly (do not rely on the guest row having
                        // no password hash). A temp-password user is an authenticated, non-guest
                        // principal, so mutationAuthorizationManager permits the forced-rotation POST.
                        .requestMatchers(HttpMethod.POST, "/app/profile/password")
                            .access(mutationAuthorizationManager)
                        .requestMatchers(WebConfiguration.APP_PATH_PATTERN).permitAll()
                        .anyRequest().permitAll();
                }
            })
            .logout(it -> it
                .logoutUrl("/logout")
                .logoutSuccessUrl("/app/reports")
                .invalidateHttpSession(true)
                .clearAuthentication(true))
            .httpBasic(it -> it.realmName("Allure Server"));

        if (enableOAuth2) {
            http.oauth2Login(withDefaults());
        }

        return http.build();
    }

    /**
     * Disables the servlet-container auto-registration of {@link ApiTokenAuthenticationFilter}.
     * <p>
     * Because the filter is a {@link org.springframework.stereotype.Component} extending
     * {@link org.springframework.web.filter.OncePerRequestFilter}, Spring Boot would otherwise
     * also register it directly in the main servlet filter chain — making it run a second time,
     * outside Spring Security's ordering (e.g. on static / error dispatches), populating the
     * {@link org.springframework.security.core.context.SecurityContextHolder} before the security
     * chain even runs. Setting {@code enabled=false} here ensures the filter executes ONLY where
     * {@code addFilterBefore(...)} places it inside the {@link SecurityFilterChain}.
     */
    @Bean
    public FilterRegistrationBean<ApiTokenAuthenticationFilter> apiTokenFilterRegistration(
        ApiTokenAuthenticationFilter filter) {
        final FilterRegistrationBean<ApiTokenAuthenticationFilter> registration =
            new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public AuthenticationManager authenticationManager(ApplicationEventPublisher eventPublisher) {
        final DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        final ProviderManager providerManager = new ProviderManager(provider);
        // Publish AuthenticationSuccessEvent so LastLoginListener can stamp lastLoginAt.
        providerManager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(eventPublisher));
        return providerManager;
    }

    /**
     * Emits a prominent startup WARN when the effective security posture is OPEN — i.e. the
     * runtime {@code requireApiAuth} toggle is {@code false} AND legacy {@code basic.auth.enable}
     * is {@code false}. In that state {@code /api/**} and {@code /allure/**} are anonymously
     * reachable (guest fallback).
     * <p>
     * The value is read straight from the persisted settings row (falling back to the yaml
     * bootstrap default when the row is not yet present) rather than from
     * {@link SystemSettingsService}'s in-memory cache. Both are {@link ApplicationRunner}s with no
     * relative {@code @Order}, so consulting the cache here could observe an unprimed value and
     * emit a false OPEN-POSTURE warning; the authoritative DB read makes this deterministic
     * regardless of runner ordering (CF-4).
     */
    @Bean
    public ApplicationRunner openPostureStartupWarning() {
        return args -> {
            final boolean requireApiAuth = systemSettingsRepository.findById(SystemSettingsEntity.SINGLETON_ID)
                .map(SystemSettingsEntity::isRequireApiAuth)
                .orElse(apiAuthBootstrapDefault);
            if (!requireApiAuth && !legacyBasicAuthEnabled) {
                log.warn("[ALLURE SERVER SECURITY] OPEN POSTURE: 'require API auth' is OFF and "
                    + "'basic.auth.enable' is false — /api/** and /allure/** are ANONYMOUSLY "
                    + "REACHABLE (guest fallback). To lock down: enable the 'require API auth' "
                    + "toggle in /app/admin/settings (runtime, authoritative) OR set "
                    + "'basic.auth.enable=true' (deprecated). This is intentional backward-compat "
                    + "behavior; ignore if this server is meant to be publicly readable.");
            }
        };
    }

    ///// PRIVATE /////

    private AuthorizationManager<RequestAuthorizationContext> apiAuthorizationManager() {
        final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
        return (authenticationSupplier, context) -> {
            if (!systemSettingsService.isRequireApiAuth()) {
                return new AuthorizationDecision(true);
            }
            final Authentication authentication = authenticationSupplier.get();
            if (authentication == null || !authentication.isAuthenticated()
                || trustResolver.isAnonymous(authentication)) {
                return new AuthorizationDecision(false);
            }
            // Reject the shared GUEST principal: an API token bound to guest must not
            // unlock the protected /api/** surface (defense for the requireApiAuth toggle).
            final boolean isGuest = authentication.getAuthorities().stream()
                .anyMatch(a -> ROLE_GUEST.equals(a.getAuthority()));
            return new AuthorizationDecision(!isGuest);
        };
    }

    /**
     * Grants only to a non-anonymous principal that is NOT the shared {@code GUEST}
     * role. Protects mutating {@code /app/**} handlers (upload, delete, generate,
     * token minting) so an anonymous visitor resolving to the seeded guest cannot
     * perform writes or mint API tokens. CSRF is disabled, so this is the guard
     * that actually protects write paths.
     */
    private AuthorizationManager<RequestAuthorizationContext> mutationAuthorizationManager() {
        final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
        return (authenticationSupplier, context) -> {
            final Authentication authentication = authenticationSupplier.get();
            if (authentication == null || !authentication.isAuthenticated()
                || trustResolver.isAnonymous(authentication)) {
                return new AuthorizationDecision(false);
            }
            final boolean isGuest = authentication.getAuthorities().stream()
                .anyMatch(a -> ROLE_GUEST.equals(a.getAuthority()));
            return new AuthorizationDecision(!isGuest);
        };
    }

    private static final String ROLE_GUEST = "ROLE_" + UserRole.GUEST.name();

}
