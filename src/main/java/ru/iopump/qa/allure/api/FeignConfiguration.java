package ru.iopump.qa.allure.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import feign.RequestInterceptor;
import feign.Retryer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.iopump.qa.allure.properties.TmsProperties;

import static org.springframework.cloud.openfeign.security.OAuth2AccessTokenInterceptor.AUTHORIZATION;
import static org.springframework.cloud.openfeign.security.OAuth2AccessTokenInterceptor.BEARER;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableFeignClients(basePackages = {"ru.iopump.qa.allure.api"})
@ImportAutoConfiguration({FeignAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class})
public class FeignConfiguration {

    @Bean
    public RequestInterceptor feignRequestInterceptor(TmsProperties props) {
        return requestTemplate -> {
            var token = props.getToken();
            var hasAuthorization = requestTemplate.headers().containsKey(AUTHORIZATION) && requestTemplate.headers().get(AUTHORIZATION).contains(token);
            if (!hasAuthorization) {
                requestTemplate.removeHeader(AUTHORIZATION);
                requestTemplate.header(AUTHORIZATION, BEARER + " " + token);
            }
        };
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100, 1000, 2);
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.serializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
