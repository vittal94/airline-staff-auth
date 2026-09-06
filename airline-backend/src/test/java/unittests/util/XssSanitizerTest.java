package unittests.util;

import com.airline.airlinebackend.util.XssSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link XssSanitizer}.
 * <p>
 * Covers null handling, plain-text preservation, HTML/script removal,
 * HTML entity encoding, email/name sanitization, and XSS detection.
 */
public class XssSanitizerTest {

    @Nested
    @DisplayName("sanitize()")
    class SanitizeTests {

        @Test
        @DisplayName("should return null when input is null")
        void sanitize_nullInput_returnsNull() {
            // Act & Assert
            assertThat(XssSanitizer.sanitize(null)).isNull();
        }

        @Test
        @DisplayName("should preserve valid plain text input")
        void sanitize_validPlainText_returnsSameText() {
            // Arrange
            String input = "sanitize test";

            // Act
            String cleaned = XssSanitizer.sanitize(input);

            // Assert
            assertThat(cleaned).isEqualTo(input);
        }

        @Test
        @DisplayName("should remove HTML tags and trim whitespace")
        void sanitize_inputWithHtmlTags_returnsCleanedText() {
            // Arrange
            String input = "<script>alert('XSS')</script> & <b>Hello</b>";

            // Act
            String cleaned = XssSanitizer.sanitize(input);

            // Assert
            assertThat(cleaned).isEqualTo("&amp; Hello");
        }

        @Test
        @DisplayName("should trim leading and trailing whitespace")
        void sanitize_inputWithWhitespace_returnsTrimmedText() {
            // Arrange
            String input = "  clean text  ";

            // Act
            String cleaned = XssSanitizer.sanitize(input);

            // Assert
            assertThat(cleaned).isEqualTo("clean text");
        }

        @Test
        @DisplayName("should return empty string for empty input")
        void sanitize_emptyInput_returnsEmptyString() {
            // Act & Assert
            assertThat(XssSanitizer.sanitize("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("encode()")
    class EncodeTests {

        @Test
        @DisplayName("should return null when input is null")
        void encode_nullInput_returnsNull() {
            // Act & Assert
            assertThat(XssSanitizer.encode(null)).isNull();
        }

        @Test
        @DisplayName("should preserve valid plain text input")
        void encode_validPlainText_returnsSameText() {
            // Arrange
            String input = "plain text";

            // Act
            String encoded = XssSanitizer.encode(input);

            // Assert
            assertThat(encoded).isEqualTo(input);
        }

        @Test
        @DisplayName("should encode special HTML characters")
        void encode_inputWithHtmlCharacters_returnsEncodedText() {
            // Arrange
            String input = "<script>alert(\"XSS\") & 'test'</script>";

            // Act
            String encoded = XssSanitizer.encode(input);

            // Assert
            assertThat(encoded).isEqualTo("&lt;script&gt;alert(&#34;XSS&#34;) &amp; &#39;test&#39;&lt;/script&gt;");
        }
    }

    @Nested
    @DisplayName("sanitizeEmail()")
    class SanitizeEmailTests {

        @Test
        @DisplayName("should return null when input is null")
        void sanitizeEmail_nullInput_returnsNull() {
            // Act & Assert
            assertThat(XssSanitizer.sanitizeEmail(null)).isNull();
        }

        @Test
        @DisplayName("should remove HTML tags and dangerous characters")
        void sanitizeEmail_inputWithHtmlAndDangerousChars_returnsCleanedEmail() {
            // Arrange
            String input = "<b>User</b>@example.com<script>alert(1)</script>";

            // Act
            String cleaned = XssSanitizer.sanitizeEmail(input);

            // Assert
            assertThat(cleaned).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("should convert email to lowercase and trim whitespace")
        void sanitizeEmail_inputWithUpperCaseAndWhitespace_returnsLowercasedTrimmedEmail() {
            // Arrange
            String input = "  User@Example.COM  ";

            // Act
            String cleaned = XssSanitizer.sanitizeEmail(input);

            // Assert
            assertThat(cleaned).isEqualTo("user@example.com");
        }
    }

    @Nested
    @DisplayName("sanitizeName()")
    class SanitizeNameTests {

        @Test
        @DisplayName("should return null when input is null")
        void sanitizeName_nullInput_returnsNull() {
            // Act & Assert
            assertThat(XssSanitizer.sanitizeName(null)).isNull();
        }

        @Test
        @DisplayName("should remove HTML tags and dangerous characters but keep legitimate name characters")
        void sanitizeName_inputWithHtmlAndDangerousChars_returnsCleanedName() {
            // Arrange
            String input = "<script>alert('XSS')</script> O'Connor & Co.;";

            // Act
            String cleaned = XssSanitizer.sanitizeName(input);

            // Assert
            assertThat(cleaned).isEqualTo("O'Connor amp Co.");
        }

        @Test
        @DisplayName("should trim leading and trailing whitespace")
        void sanitizeName_inputWithWhitespace_returnsTrimmedName() {
            // Arrange
            String input = "  John Doe  ";

            // Act
            String cleaned = XssSanitizer.sanitizeName(input);

            // Assert
            assertThat(cleaned).isEqualTo("John Doe");
        }
    }

    @Nested
    @DisplayName("containsXss()")
    class ContainsXssTests {

        @Test
        @DisplayName("should return false when input is null")
        void containsXss_nullInput_returnsFalse() {
            // Act & Assert
            assertThat(XssSanitizer.containsXss(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for clean plain text input")
        void containsXss_cleanInput_returnsFalse() {
            // Act & Assert
            assertThat(XssSanitizer.containsXss("clean text")).isFalse();
        }

        @Test
        @DisplayName("should return true when input contains HTML tags")
        void containsXss_inputWithHtmlTags_returnsTrue() {
            // Act & Assert
            assertThat(XssSanitizer.containsXss("<script>alert(1)</script>")).isTrue();
        }
    }
}
