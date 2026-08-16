package ru.iopump.qa.allure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SwaggerBrandingFilter")
class SwaggerBrandingFilterTest {

    private static final String SWAGGER_HTML = """
        <!DOCTYPE html>
        <html><head><title>Swagger UI</title></head>
        <body><div id="swagger-ui"></div></body></html>
        """;

    private static final String EXPECTED_HEAD_FAVICON_ICO = "<link rel=\"icon\" href=\"/favicon.ico\" sizes=\"48x48\">";
    private static final String EXPECTED_HEAD_ICON = "<link rel=\"icon\" href=\"/icon.svg\">";
    private static final String EXPECTED_HEAD_THEME = "href=\"/swagger/theme.css\"";
    private static final String EXPECTED_BODY_SCRIPT = "<script src=\"/swagger/brand.js\" defer></script>";

    private final SwaggerBrandingFilter filter = new SwaggerBrandingFilter();

    /** Chain that emulates the upstream resource handler emitting bytes + content type into the wrapper. */
    private static FilterChain emitting(String body, String contentType, Charset charset) {
        return (ServletRequest req, ServletResponse res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setContentType(contentType);
            http.setCharacterEncoding(charset.name());
            byte[] bytes = body.getBytes(charset);
            http.setContentLength(bytes.length);
            http.getOutputStream().write(bytes);
        };
    }

    private static FilterChain emittingNothing() {
        return (ServletRequest req, ServletResponse res) -> { /* redirect / 304: no body */ };
    }

    @Test
    @DisplayName("should inject head and body branding markup into the swagger-ui index.html response")
    void should_inject_branding_into_swagger_index() throws ServletException, IOException {
        // GIVEN a request for the swagger-ui index.html and an upstream HTML response
        var request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        request.setRequestURI("/swagger-ui/index.html");
        var response = new MockHttpServletResponse();
        var chain = emitting(SWAGGER_HTML, "text/html", StandardCharsets.UTF_8);

        // WHEN the filter processes the response
        filter.doFilterInternal(request, response, chain);

        // THEN the branding head and body markup are present in the rewritten HTML
        String out = response.getContentAsString();
        assertThat(out)
            .as("head branding (favicon.ico, svg icon, theme css, theme bootstrap) injected before </head>")
            .contains(EXPECTED_HEAD_FAVICON_ICO)
            .contains(EXPECTED_HEAD_ICON)
            .contains(EXPECTED_HEAD_THEME)
            .contains("allure-server-theme");
        assertThat(out)
            .as("body branding script injected before </body>")
            .contains(EXPECTED_BODY_SCRIPT);
        assertThat(out.indexOf(EXPECTED_HEAD_ICON))
            .as("head markup precedes body markup in document order")
            .isLessThan(out.indexOf(EXPECTED_BODY_SCRIPT));
    }

    @Test
    @DisplayName("should set Content-Length to the byte length of the rewritten body")
    void should_correct_content_length_after_injection() throws ServletException, IOException {
        // GIVEN a swagger index.html request
        var request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        request.setRequestURI("/swagger-ui/index.html");
        var response = new MockHttpServletResponse();
        var chain = emitting(SWAGGER_HTML, "text/html;charset=UTF-8", StandardCharsets.UTF_8);

        // WHEN the filter rewrites the body
        filter.doFilterInternal(request, response, chain);

        // THEN Content-Length equals the UTF-8 byte length of the emitted body (not the upstream length)
        int expectedLength = response.getContentAsString().getBytes(StandardCharsets.UTF_8).length;
        assertThat(response.getContentLength())
            .as("Content-Length matches rewritten UTF-8 body length")
            .isEqualTo(expectedLength);
    }

    @Test
    @DisplayName("should re-encode a non-UTF-8 upstream body as UTF-8 without mojibake")
    void should_reencode_non_utf8_body_as_utf8() throws ServletException, IOException {
        // GIVEN a swagger response declared as ISO-8859-1 containing a non-ASCII character
        String htmlWithAccent = SWAGGER_HTML.replace("Swagger UI", "Swägger UI");
        var request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        request.setRequestURI("/swagger-ui/index.html");
        var response = new MockHttpServletResponse();
        var chain = emitting(htmlWithAccent, "text/html", StandardCharsets.ISO_8859_1);

        // WHEN the filter rewrites the body
        filter.doFilterInternal(request, response, chain);

        // THEN the body is decoded with the upstream charset but always re-emitted as UTF-8
        //   (the upstream ISO-8859-1 header must not survive and mojibake the injected brand.js strings)
        byte[] bytes = response.getContentAsByteArray();
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        assertThat(decoded)
            .as("non-ASCII title re-encoded to UTF-8 without mojibake and branding injected")
            .contains("Swägger UI")
            .contains(EXPECTED_HEAD_ICON);
        assertThat(response.getCharacterEncoding())
            .as("response character encoding forced to UTF-8")
            .isEqualTo(StandardCharsets.UTF_8.name());
    }

    @Test
    @DisplayName("should prefix injected asset URLs with the request context path")
    void should_prefix_asset_urls_with_context_path() throws ServletException, IOException {
        // GIVEN a swagger request served under a non-root context path "/server"
        var request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        request.setRequestURI("/server/swagger-ui/index.html");
        request.setContextPath("/server");
        var response = new MockHttpServletResponse();
        var chain = emitting(SWAGGER_HTML, "text/html", StandardCharsets.UTF_8);

        // WHEN the filter rewrites the response
        filter.doFilterInternal(request, response, chain);

        // THEN every injected asset URL is prefixed with the context path so it resolves under /server
        String out = response.getContentAsString();
        assertThat(out)
            .as("favicon set, theme css and brand js URLs prefixed with the context path")
            .contains("href=\"/server/favicon.ico\"")
            .contains("href=\"/server/icon.svg\"")
            .contains("href=\"/server/swagger/theme.css\"")
            .contains("<script src=\"/server/swagger/brand.js\" defer></script>");
        assertThat(out)
            .as("no root-absolute (context-less) asset URLs remain")
            .doesNotContain("href=\"/favicon.ico\"")
            .doesNotContain("href=\"/icon.svg\"")
            .doesNotContain("src=\"/swagger/brand.js\"");
    }

    @Test
    @DisplayName("should keep root-absolute asset URLs when the context path is empty")
    void should_keep_root_absolute_urls_for_empty_context_path() throws ServletException, IOException {
        // GIVEN a swagger request under the default (empty) context path
        var request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        request.setRequestURI("/swagger-ui/index.html");
        request.setContextPath("");
        var response = new MockHttpServletResponse();
        var chain = emitting(SWAGGER_HTML, "text/html", StandardCharsets.UTF_8);

        // WHEN the filter rewrites the response
        filter.doFilterInternal(request, response, chain);

        // THEN the asset URLs stay root-absolute (no stray double slashes)
        String out = response.getContentAsString();
        assertThat(out)
            .as("empty context path yields plain root-absolute asset URLs")
            .contains(EXPECTED_HEAD_ICON)
            .contains(EXPECTED_HEAD_THEME)
            .contains(EXPECTED_BODY_SCRIPT)
            .doesNotContain("//icon.svg")
            .doesNotContain("//swagger/");
    }

    @Test
    @DisplayName("should inject a context path containing a '$' verbatim without regex backreference mangling")
    void should_inject_dollar_in_context_path_verbatim() throws ServletException, IOException {
        // GIVEN a context path that contains a regex replacement metacharacter '$'
        //   String.replaceFirst treats '$' / '\' in the replacement as group references, so without
        //   Matcher.quoteReplacement this would throw or corrupt the output.
        var request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        request.setRequestURI("/ctx$1/swagger-ui/index.html");
        request.setContextPath("/ctx$1");
        var response = new MockHttpServletResponse();
        var chain = emitting(SWAGGER_HTML, "text/html", StandardCharsets.UTF_8);

        // WHEN the filter rewrites the response
        filter.doFilterInternal(request, response, chain);

        // THEN the '$1' is emitted literally in every asset URL, not interpreted as a backreference
        String out = response.getContentAsString();
        assertThat(out)
            .as("'$' in the replacement is treated as a literal, asset URLs emitted verbatim")
            .contains("href=\"/ctx$1/icon.svg\"")
            .contains("href=\"/ctx$1/swagger/theme.css\"")
            .contains("<script src=\"/ctx$1/swagger/brand.js\" defer></script>");
    }

    @Test
    @DisplayName("should pass non-matching URIs straight through without buffering")
    void should_pass_through_non_matching_uri() throws ServletException, IOException {
        // GIVEN a request to an unrelated resource
        var request = new MockHttpServletRequest("GET", "/api/report");
        request.setRequestURI("/api/report");
        var response = new MockHttpServletResponse();
        String upstream = "{\"reports\":[]}";
        var chain = emitting(upstream, MediaType.APPLICATION_JSON_VALUE, StandardCharsets.UTF_8);

        // WHEN the filter runs
        filter.doFilterInternal(request, response, chain);

        // THEN the body is delivered verbatim with no branding injection
        assertThat(response.getContentAsString())
            .as("non-swagger URI passes through untouched")
            .isEqualTo(upstream);
    }

    @Test
    @DisplayName("should copy a non-HTML swagger response through untouched")
    void should_pass_through_non_html_body() throws ServletException, IOException {
        // GIVEN the swagger URI but a non-HTML (JSON) upstream body
        var request = new MockHttpServletRequest("GET", "/swagger-ui.html");
        request.setRequestURI("/swagger-ui.html");
        var response = new MockHttpServletResponse();
        String json = "{\"openapi\":\"3.0.1\"}";
        var chain = emitting(json, MediaType.APPLICATION_JSON_VALUE, StandardCharsets.UTF_8);

        // WHEN the filter runs
        filter.doFilterInternal(request, response, chain);

        // THEN the JSON is copied through without branding markup
        assertThat(response.getContentAsString())
            .as("non-HTML body copied through unchanged")
            .isEqualTo(json);
        assertThat(response.getContentAsString())
            .as("no branding injected into non-HTML body")
            .doesNotContain(EXPECTED_HEAD_ICON);
    }

    @Test
    @DisplayName("should copy an empty swagger response (redirect/304) through untouched")
    void should_pass_through_empty_body() throws ServletException, IOException {
        // GIVEN the swagger URI but an empty upstream body (redirect / 304)
        var request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        request.setRequestURI("/swagger-ui/index.html");
        var response = new MockHttpServletResponse();
        var chain = emittingNothing();

        // WHEN the filter runs
        filter.doFilterInternal(request, response, chain);

        // THEN nothing is written and no branding markup leaks
        assertThat(response.getContentAsByteArray())
            .as("empty upstream body stays empty").isEmpty();
    }
}
