package ru.iopump.qa.allure.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.iopump.qa.allure.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UserRole} has exactly 3 constants (GUEST, USER, ADMIN) and {@link TokenPolicy}'s
 * internal map covers all of them, so the defensive {@code IllegalStateException} for an
 * "unmapped role" is unreachable via any real {@link UserRole} value and is intentionally
 * not exercised here — asserting it would require reflection to forge a role outside the
 * enum, which is disproportionate for covering dead defensive code.
 */
class TokenPolicyTest {

    private final TokenPolicy tokenPolicy = new TokenPolicy();

    @ParameterizedTest(name = "role={0} -> maxActiveTokens={1}")
    @DisplayName("should return the per-role active token cap")
    @CsvSource({
        "GUEST, 0",
        "USER, 10",
        "ADMIN, 50"
    })
    void maxActiveTokens_returnsConfiguredCapPerRole(UserRole role, int expectedCap) {
        // GIVEN — a real UserRole constant

        // WHEN
        final int result = tokenPolicy.maxActiveTokens(role);

        // THEN
        assertThat(result)
            .as("role '%s' active-token cap", role)
            .isEqualTo(expectedCap);
    }

    @Test
    @DisplayName("should throw NullPointerException when role is null")
    void maxActiveTokens_throwsNullPointerException_whenRoleIsNull() {
        // GIVEN — maxActiveTokens is annotated @NonNull on its role parameter

        // WHEN / THEN
        assertThatThrownBy(() -> tokenPolicy.maxActiveTokens(null))
            .as("null role must be rejected by the Lombok @NonNull guard")
            .isInstanceOf(NullPointerException.class);
    }
}
