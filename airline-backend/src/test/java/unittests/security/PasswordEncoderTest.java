package unittests.security;

import com.airline.airlinebackend.security.PasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PasswordEncoder}.
 * <p>
 * Covers password hashing and verification via Argon2id, as well as
 * SHA-256 token hashing and verification.
 */
@DisplayName("PasswordEncoder")
class PasswordEncoderTest {

    private static final String VALID_PASSWORD = "!123PasswordTest.";
    private static final Pattern ARGON2_HASH_PATTERN = Pattern.compile(
            "^\\$argon2id\\$v=\\d+\\$m=\\d+,t=\\d+,p=\\d+\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+$"
    );

    @Nested
    @DisplayName("encode()")
    class EncodeTests {

        @Test
        @DisplayName("should return a valid Argon2id formatted hash for a valid password")
        void encode_validPassword_returnsArgon2idFormattedHash() {
            // Act
            String encodedHash = PasswordEncoder.encode(VALID_PASSWORD);

            // Assert
            assertThat(encodedHash)
                    .isNotNull()
                    .matches(ARGON2_HASH_PATTERN.pattern());
        }

        @Test
        @DisplayName("should produce different hashes for the same password due to random salt")
        void encode_samePasswordCalledTwice_producesDifferentHashes() {
            // Act
            String firstHash = PasswordEncoder.encode(VALID_PASSWORD);
            String secondHash = PasswordEncoder.encode(VALID_PASSWORD);

            // Assert
            assertThat(firstHash)
                    .isNotNull()
                    .isNotEqualTo(secondHash);
        }

        @Test
        @DisplayName("should throw NullPointerException when password is null")
        void encode_nullPassword_throwsNullPointerException() {
            // Act & Assert
            assertThatThrownBy(() -> PasswordEncoder.encode(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("verify()")
    class VerifyTests {

        @Test
        @DisplayName("should return true when password matches the encoded hash")
        void verify_correctPassword_returnsTrue() {
            // Arrange
            String encodedHash = PasswordEncoder.encode(VALID_PASSWORD);

            // Act
            boolean result = PasswordEncoder.verify(VALID_PASSWORD, encodedHash);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when password does not match the encoded hash")
        void verify_wrongPassword_returnsFalse() {
            // Arrange
            String encodedHash = PasswordEncoder.encode(VALID_PASSWORD);

            // Act
            boolean result = PasswordEncoder.verify("wrongPassword", encodedHash);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when password is null")
        void verify_nullPassword_returnsFalse() {
            // Arrange
            String encodedHash = PasswordEncoder.encode(VALID_PASSWORD);

            // Act
            boolean result = PasswordEncoder.verify(null, encodedHash);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when encoded hash format is invalid")
        void verify_invalidHashFormat_returnsFalse() {
            // Act
            boolean result = PasswordEncoder.verify(VALID_PASSWORD, "not-a-valid-hash");

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when encoded hash has been tampered with")
        void verify_tamperedHash_returnsFalse() {
            // Arrange
            String encodedHash = PasswordEncoder.encode(VALID_PASSWORD);
            String tamperedHash = encodedHash.substring(0, encodedHash.length() - 1) + "X";

            // Act
            boolean result = PasswordEncoder.verify(VALID_PASSWORD, tamperedHash);

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("hashToken()")
    class HashTokenTests {

        @Test
        @DisplayName("should produce the same hash for the same token")
        void hashToken_sameInput_producesSameHash() {
            // Arrange
            String token = "remember-me-token";

            // Act
            String firstHash = PasswordEncoder.hashToken(token);
            String secondHash = PasswordEncoder.hashToken(token);

            // Assert
            assertThat(firstHash)
                    .isNotNull()
                    .isEqualTo(secondHash);
        }

        @Test
        @DisplayName("should produce different hashes for different tokens")
        void hashToken_differentInput_producesDifferentHash() {
            // Act
            String firstHash = PasswordEncoder.hashToken("token-one");
            String secondHash = PasswordEncoder.hashToken("token-two");

            // Assert
            assertThat(firstHash)
                    .isNotNull()
                    .isNotEqualTo(secondHash);
        }
    }

    @Nested
    @DisplayName("verifyToken()")
    class VerifyTokenTests {

        @Test
        @DisplayName("should return true when token matches the hash")
        void verifyToken_matchingToken_returnsTrue() {
            // Arrange
            String token = "remember-me-token";
            String hash = PasswordEncoder.hashToken(token);

            // Act
            boolean result = PasswordEncoder.verifyToken(token, hash);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when token does not match the hash")
        void verifyToken_nonMatchingToken_returnsFalse() {
            // Arrange
            String hash = PasswordEncoder.hashToken("expected-token");

            // Act
            boolean result = PasswordEncoder.verifyToken("wrong-token", hash);

            // Assert
            assertThat(result).isFalse();
        }
    }
}
