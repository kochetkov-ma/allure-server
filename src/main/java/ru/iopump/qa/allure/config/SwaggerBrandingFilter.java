package ru.iopump.qa.allure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

/**
 * Injects Brew.QA branding (favicon, theme CSS, theme-bootstrap and brand JS) into the Swagger UI
 * {@code index.html} responses. Buffers the upstream response with Spring's
 * {@link ContentCachingResponseWrapper}, rewrites the HTML {@code <head>}/{@code <body>}, and flushes
 * the patched bytes with a corrected {@code Content-Length}. Non-HTML or empty bodies (redirects,
 * 304s, static assets) are copied through untouched.
 *
 * <p>Asset URLs are prefixed with the request's {@link HttpServletRequest#getContextPath() context
 * path} so the branding resolves correctly when the application is deployed under a non-root
 * {@code server.servlet.context-path}.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class SwaggerBrandingFilter extends OncePerRequestFilter {

    /** {@code %s} is substituted with the request context path (empty for a root deployment). */
    private static final String HEAD_INJECT_TEMPLATE = """
            <link rel="icon" href="%1$s/favicon.ico" sizes="48x48">
            <link rel="icon" href="%1$s/icon.svg">
            <link rel="stylesheet" type="text/css" href="%1$s/swagger/theme.css" />
            <script>(function(){try{var s=localStorage.getItem('allure-server-theme');var p=matchMedia('(prefers-color-scheme: light)').matches?'light':'dark';document.documentElement.dataset.theme=s||p;}catch(e){document.documentElement.dataset.theme='dark';}})();</script>
            </head>""";

    private static final String BODY_INJECT_TEMPLATE = """
            <script src="%1$s/swagger/brand.js" defer></script>
            </body>""";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        final String uri = request.getRequestURI();
        if (!uri.endsWith("/swagger-ui/index.html") && !"/swagger-ui.html".equals(uri)) {
            chain.doFilter(request, response);
            return;
        }

        final ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);

        final byte[] body = wrapper.getContentAsByteArray();
        final String contentType = wrapper.getContentType();
        if (body.length == 0 || contentType == null || !contentType.contains(MediaType.TEXT_HTML_VALUE)) {
            // redirect / 304 / non-HTML asset — pass the original bytes through untouched
            wrapper.copyBodyToResponse();
            return;
        }

        // getContextPath() is "" for a root deployment and e.g. "/server" under a configured context path.
        final String contextPath = request.getContextPath();
        final String headInject = HEAD_INJECT_TEMPLATE.formatted(contextPath);
        final String bodyInject = BODY_INJECT_TEMPLATE.formatted(contextPath);

        final Charset charset = resolveCharset(wrapper.getCharacterEncoding());
        String html = new String(body, charset);
        // quoteReplacement: the injected HTML is a literal — never interpret '$'/'\' as regex backreferences.
        html = html.replaceFirst("(?i)</head>", Matcher.quoteReplacement(headInject));
        html = html.replaceFirst("(?i)</body>", Matcher.quoteReplacement(bodyInject));

        // Always emit UTF-8: the upstream header may say ISO-8859-1, which would override the
        // page's <meta charset> and mojibake non-ASCII strings set by the injected brand.js.
        final byte[] out = html.getBytes(StandardCharsets.UTF_8);
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(out.length);
        response.getOutputStream().write(out);
        response.flushBuffer();
    }

    private static Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }
}
