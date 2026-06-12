package ru.iopump.qa.allure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Duration;

/**
 * Binds the "New API token" dialog form on {@code /app/profile}.
 * {@link TokenExpiration} is a closed set; the template drives the {@code <select>} from it.
 */
public record CreateTokenForm(
    @NotBlank @Size(max = 128) String name,
    @NotNull TokenExpiration expiration
) {

    public Duration ttl() {
        return expiration.ttl();
    }

    public enum TokenExpiration {
        DAYS_7("7 days", Duration.ofDays(7)),
        DAYS_30("30 days", Duration.ofDays(30)),
        DAYS_90("90 days", Duration.ofDays(90)),
        DAYS_180("180 days", Duration.ofDays(180)),
        DAYS_365("365 days", Duration.ofDays(365)),
        NEVER("Never", null);

        private final String label;
        private final Duration ttl;

        TokenExpiration(String label, Duration ttl) {
            this.label = label;
            this.ttl = ttl;
        }

        public String label() {
            return label;
        }

        public Duration ttl() {
            return ttl;
        }
    }
}
