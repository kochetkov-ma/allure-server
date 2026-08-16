package ru.iopump.qa.allure.web.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean-validation regression coverage for {@link CreateUserForm#username()}'s
 * {@code @Pattern}, which is the only guard against a username value that could break
 * out of a JS-string/HTML-attribute context if ever interpolated unescaped.
 */
class CreateUserFormTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest(name = "username=\"{0}\"")
    @DisplayName("should reject usernames containing characters unsafe in JS-string/HTML-attribute contexts")
    @ValueSource(strings = {
        "<script>",
        "\"onmouseover=alert(1)",
        "'; DROP TABLE app_user;--",
        "back\\slash",
        "semi;colon",
        "space name"
    })
    void username_rejectsUnsafeCharacters(String hostileUsername) {
        // GIVEN — a form with an otherwise-valid displayName and a hostile username

        // WHEN
        final Set<ConstraintViolation<CreateUserForm>> violations =
            validator.validate(new CreateUserForm(hostileUsername, "Display"));

        // THEN
        assertThat(violations)
            .as("username '%s' must violate the @Pattern constraint", hostileUsername)
            .anySatisfy(violation -> assertThat(violation.getPropertyPath())
                .as("violation must be reported against the 'username' property")
                .hasToString("username"));
    }

    @ParameterizedTest(name = "username=\"{0}\"")
    @DisplayName("should accept usernames restricted to letters, digits, dot, underscore and hyphen")
    @ValueSource(strings = {"alice", "bob.smith", "user_name", "user-42", "ABC123"})
    void username_acceptsAllowedCharacters(String safeUsername) {
        // GIVEN — a form with a username composed only of allowed characters

        // WHEN
        final Set<ConstraintViolation<CreateUserForm>> violations =
            validator.validate(new CreateUserForm(safeUsername, "Display"));

        // THEN
        assertThat(violations)
            .as("username '%s' must satisfy the @Pattern constraint", safeUsername)
            .isEmpty();
    }
}
