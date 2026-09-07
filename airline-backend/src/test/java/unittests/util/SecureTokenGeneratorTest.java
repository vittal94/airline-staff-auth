package unittests.util;

import com.airline.airlinebackend.util.SecureTokenGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SecureTokenGenerator}.
 *
 * <p>These tests verify that the utility produces cryptographically secure,
 * URL-safe Base64 tokens of the expected encoded length, that every public
 * convenience method delegates to the correct byte length, and that generated
 * tokens are unique across many invocations.</p>
 */
@DisplayName("SecureTokenGenerator")
public class SecureTokenGeneratorTest {

    private static final Pattern BASE64_URL_SAFE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    // ============================================================
    // generateToken(int)
    // ============================================================

    @Nested
    @DisplayName("generateToken(int)")
    class GenerateTokenTests {

        @ParameterizedTest(name = "byteLength={0} -> encodedLength={1}")
        @CsvSource({
                "1, 2",
                "2, 3",
                "3, 4",
                "15, 20",
                "16, 22",
                "24, 32",
                "32, 43",
                "48, 64"
        })
        @DisplayName("returns URL-safe Base64 string of expected length")
        void generateToken_validByteLength_returnsExpectedEncodedLength(int byteLength, int expectedEncodedLength) {
            // Act
            String token = SecureTokenGenerator.generateToken(byteLength);

            // Assert
            assertThat(token)
                    .hasSize(expectedEncodedLength)
                    .matches(BASE64_URL_SAFE_PATTERN);
        }

        @Test
        @DisplayName("returns empty string for zero byte length")
        void generateToken_zeroByteLength_returnsEmptyString() {
            // Act
            String token = SecureTokenGenerator.generateToken(0);

            // Assert
            assertThat(token).isEmpty();
        }

        @ParameterizedTest(name = "byteLength={0}")
        @ValueSource(ints = {-1, -8, Integer.MIN_VALUE})
        @DisplayName("throws NegativeArraySizeException for negative byte length")
        void generateToken_negativeByteLength_throwsNegativeArraySizeException(int byteLength) {
            // Act & Assert
            assertThatThrownBy(() -> SecureTokenGenerator.generateToken(byteLength))
                    .isInstanceOf(NegativeArraySizeException.class);
        }

        @Test
        @DisplayName("produces distinct tokens across many invocations")
        void generateToken_manyCalls_producesUniqueTokens() {
            // Arrange
            int invocationCount = 1_000;

            // Act
            Set<String> tokens = new HashSet<>(invocationCount);
            IntStream.range(0, invocationCount)
                    .forEach(i -> tokens.add(SecureTokenGenerator.generateToken(32)));

            // Assert
            assertThat(tokens).hasSize(invocationCount);
        }
    }

    // ============================================================
    // Convenience methods
    // ============================================================

    @Nested
    @DisplayName("generateSessionId()")
    class GenerateSessionIdTests {

        @Test
        @DisplayName("returns 43-character URL-safe Base64 token")
        void generateSessionId_returns43CharUrlSafeToken() {
            // Act
            String sessionId = SecureTokenGenerator.generateSessionId();

            // Assert
            assertThat(sessionId)
                    .hasSize(43)
                    .matches(BASE64_URL_SAFE_PATTERN);
        }
    }

    @Nested
    @DisplayName("generateCsrfToken()")
    class GenerateCsrfTokenTests {

        @Test
        @DisplayName("returns 32-character URL-safe Base64 token")
        void generateCsrfToken_returns32CharUrlSafeToken() {
            // Act
            String csrfToken = SecureTokenGenerator.generateCsrfToken();

            // Assert
            assertThat(csrfToken)
                    .hasSize(32)
                    .matches(BASE64_URL_SAFE_PATTERN);
        }
    }

    @Nested
    @DisplayName("generateSeries()")
    class GenerateSeriesTests {

        @Test
        @DisplayName("returns 22-character URL-safe Base64 token")
        void generateSeries_returns22CharUrlSafeToken() {
            // Act
            String series = SecureTokenGenerator.generateSeries();

            // Assert
            assertThat(series)
                    .hasSize(22)
                    .matches(BASE64_URL_SAFE_PATTERN);
        }
    }

    @Nested
    @DisplayName("generateRememberMeToken()")
    class GenerateRememberMeTokenTests {

        @Test
        @DisplayName("returns 43-character URL-safe Base64 token")
        void generateRememberMeToken_returns43CharUrlSafeToken() {
            // Act
            String token = SecureTokenGenerator.generateRememberMeToken();

            // Assert
            assertThat(token)
                    .hasSize(43)
                    .matches(BASE64_URL_SAFE_PATTERN);
        }
    }

    @Nested
    @DisplayName("generateEmailToken()")
    class GenerateEmailTokenTests {

        @Test
        @DisplayName("returns 43-character URL-safe Base64 token")
        void generateEmailToken_returns43CharUrlSafeToken() {
            // Act
            String token = SecureTokenGenerator.generateEmailToken();

            // Assert
            assertThat(token)
                    .hasSize(43)
                    .matches(BASE64_URL_SAFE_PATTERN);
        }
    }
}
