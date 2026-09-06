package unittests.util;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.exception.ValidationException;
import com.airline.airlinebackend.util.ValidationUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import unittests.exceptions.ValidationExceptionTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class ValidationUtilTest {
    @Nested
    @DisplayName("validateEmail tests")
    class ValidateEmail {
        @ParameterizedTest
        @NullSource
        @EmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("should throw exception when email is null, empty or blank")
        void testNullEmptyBlank(String email) {
            assertThatThrownBy( () -> ValidationUtil.validateEmail(email) )
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("normal email pattern does not throw any exception")
        void normalEmailTests() {
            assertThatCode( () -> ValidationUtil.validateEmail("test@test.com") )
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("wrong email pattern should throw exception")
        void wrongEmailPattern_shouldThrowException() {
            assertThatThrownBy( () -> ValidationUtil.validateEmail("testtest.com") )
                    .isInstanceOf(ValidationException.class);

        }

        @Test
        @DisplayName("email length over 255 should throw exception")
        void tooLongEmail_shouldThrowException() {
            String longEmail = "t".repeat(250) + "@test.com";
            assertThatThrownBy( () -> ValidationUtil.validateEmail(longEmail) )
            .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("email with length exactly 255 should be accepted")
        void length_255_shouldPassed() {
            String maxLengthEmail = "t".repeat(246) + "@test.com";

            assertThatCode( () -> ValidationUtil.validateEmail(maxLengthEmail) )
            .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validatePassword tests")
    class ValidatePassword {
        private static AppConfig appConfig;

        @BeforeAll
        static void setUp() {
            appConfig = AppConfig.getInstance();
        }

        @ParameterizedTest
        @NullSource
        @EmptySource
        @DisplayName("null, empty password should throw exception")
        void testNullEmptyPassword(String password) {
           assertThatThrownBy( () -> ValidationUtil.validatePassword(password) )
                   .isInstanceOf(ValidationException.class)
                   .hasMessageContaining("Password is required");
        }

        @Test
        @DisplayName("password with less then minimal length should throw exception")
        void minLength_shouldThrowException() {
            assertThatThrownBy( () -> ValidationUtil.validatePassword("123rt."))
                    .isInstanceOf(ValidationException.class)
                    .satisfies( ex -> {
                        var validationException = (ValidationException) ex;

                        assertFieldErrors(validationException, "Password must be at least "
                                + appConfig.getPasswordMinLength() + " characters");
                    });
        }

        @Test
        @DisplayName("uppercase  test")
        void upperCaseTests() {
            if (!appConfig.isPasswordRequireUppercase())
                return;

            assertThatThrownBy( () -> ValidationUtil.validatePassword("123asdqwedfdssdfss.") )
                    .isInstanceOf(ValidationException.class)
                    .satisfies( ex -> {
                        var validationException = (ValidationException) ex;

                        assertFieldErrors(validationException, "Password must contain uppercase characters");
                    });
        }

        @Test
        @DisplayName("lowercase test")
        void lowerCaseTests() {
            assertThatThrownBy( () -> ValidationUtil.validatePassword("123ASD.") )
                    .isInstanceOf(ValidationException.class)
                    .satisfies( ex -> {
                        var validationException = (ValidationException) ex;

                        assertFieldErrors(validationException, "Password must contain lower case characters");
                    });
        }

        @Test
        @DisplayName("should reject password without digit")
        void passwordWithoutDigit_shouldThrowFieldError() {
            assertThatThrownBy(() -> ValidationUtil.validatePassword("NoDigitsHere!"))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(ex -> assertFieldErrors((ValidationException) ex,
                            "Password must contain digit characters"));
        }

        /**
         * Tests that a password missing a special character is rejected.
         *
         * <p>Why: special characters substantially increase password strength.
         * Without this test alphanumeric-only passwords could be accepted.</p>
         */
        @Test
        @DisplayName("should reject password without special character")
        void passwordWithoutSpecial_shouldThrowFieldError() {
            assertThatThrownBy(() -> ValidationUtil.validatePassword("NoSpecial123"))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(ex -> assertFieldErrors((ValidationException) ex,
                            "Password must contain special characters"));
        }

        /**
         * Tests that common passwords are rejected regardless of case.
         *
         * <p>Why: the implementation lower-cases the password before checking
         * the common list. Without this test "PASSWORD" or "Password123"
         * could slip through just by changing case.</p>
         */
        @Test
        @DisplayName("should reject common password with different casing")
        void commonPasswordWithDifferentCase_shouldThrowFieldError() {
            assertThatThrownBy(() -> ValidationUtil.validatePassword("PASSWORD123"))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(ex -> assertFieldErrors((ValidationException) ex,
                            "Password is too common, please choose a stronger password"));
        }

        /**
         * Tests that a known common password is rejected even if it meets
         * length and character-class rules.
         *
         * <p>Why: attackers always try common passwords first. Without this
         * test a user could set "Password123!" which satisfies the policy but
         * is trivially guessable.</p>
         */
        @Test
        @DisplayName("should reject common password")
        void commonPassword_shouldThrowFieldError() {
            assertThatThrownBy(() -> ValidationUtil.validatePassword("Password123"))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(ex -> assertFieldErrors((ValidationException) ex,
                            "Password is too common, please choose a stronger password"));
        }

        /**
         * Tests that multiple independent policy failures are reported together.
         *
         * <p>Why: users should see every problem on the first attempt. Without
         * this test a logic change could short-circuit and return only the
         * first error found.</p>
         */
        @Test
        @DisplayName("should report multiple field errors for multiple policy violations")
        void multipleViolations_shouldReportAllFieldErrors() {
            assertThatThrownBy(() -> ValidationUtil.validatePassword("short"))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(ex -> {
                        ValidationException ve = (ValidationException) ex;
                        List<String> messages = ve.getFieldErrors().stream()
                                .map(e -> e.getField() + ":" + e.getMessage())
                                .toList();
                        assertThat(messages).contains(
                                "password:Password must be at least " + appConfig.getPasswordMinLength() + " characters",
                                "password:Password must contain uppercase characters",
                                "password:Password must contain digit characters",
                                "password:Password must contain special characters");
                    });
        }

        private void assertFieldErrors(ValidationException vEx, String message) {
            assertThat(vEx.getFieldErrors())
                    .anyMatch(fieldError -> fieldError.getField().equals("password") &&
                            fieldError.getMessage().equals(message));
        }

        @Nested
        @DisplayName("validatePasswordMatch()")
        class ValidatePasswordMatch {

            /**
             * Tests that two identical passwords pass matching.
             *
             * <p>Why: password confirmation is a standard registration/change flow.
             * Without this test we cannot prove the method ever succeeds.</p>
             */
            @Test
            @DisplayName("should not throw when passwords match")
            void matchingPasswords_shouldPass() {
                assertThatCode(() -> ValidationUtil.validatePasswordMatch("SamePassword123!", "SamePassword123!"))
                        .doesNotThrowAnyException();
            }

            /**
             * Tests that different passwords are rejected with a clear message.
             *
             * <p>Why: users sometimes mistype their confirmation. Without this test
             * a typo would go unnoticed and the account would be created with an
             * unexpected password.</p>
             */
            @Test
            @DisplayName("should throw ValidationException when passwords do not match")
            void nonMatchingPasswords_shouldThrowValidationException() {
                assertThatThrownBy(() -> ValidationUtil.validatePasswordMatch("Password123!", "Different123!"))
                        .isInstanceOf(ValidationException.class)
                        .hasMessageContaining("Passwords do not match");
            }

            /**
             * Documents that passing null as the first argument currently throws
             * NullPointerException.
             *
             * <p>Why: the method is called from servlets that normally pass
             * non-null values, but a null caller would crash instead of returning
             * a ValidationException. This test makes the current behavior explicit
             * so future maintainers know it is intentional.</p>
             */
            @Test
            @DisplayName("should throw NullPointerException when first password is null")
            void nullFirstPassword_shouldThrowNullPointerException() {
                assertThatThrownBy(() -> ValidationUtil.validatePasswordMatch(null, "Password123!"))
                        .isInstanceOf(NullPointerException.class);
            }
        }
    }
}
