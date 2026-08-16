package ru.iopump.qa.allure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.iopump.qa.allure.security.ApiTokenAuthenticationFilter;

/**
 * Declares the OpenAPI document plus the two authentication schemes Swagger UI exposes through its
 * "Authorize" control: HTTP Basic and the {@code X-API-Token} header. The schemes are only
 * advertised (defined in {@link Components}); no global {@code SecurityRequirement} is attached, so
 * the anonymous-by-default posture of the API is preserved in the generated spec while the Authorize
 * button still renders.
 */
@Configuration
public class OpenApiConfiguration {

    static final String BASIC_SCHEME = "basicAuth";
    static final String API_TOKEN_SCHEME = "apiToken";

    @Bean
    public OpenAPI allureServerOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Allure Server API")
                .description("REST API to upload allure-results and generate allure reports")
                .version("v1"))
            .components(new Components()
                .addSecuritySchemes(BASIC_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("basic")
                    .description("HTTP Basic authentication"))
                .addSecuritySchemes(API_TOKEN_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name(ApiTokenAuthenticationFilter.HEADER_NAME)
                    .description("Static API token sent in the " + ApiTokenAuthenticationFilter.HEADER_NAME + " header")));
    }
}
