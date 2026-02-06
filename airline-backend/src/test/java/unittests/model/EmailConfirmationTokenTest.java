package unittests.model;

import com.airline.airlinebackend.model.EmailConfirmationToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmailConfirmationToken model class tests")
public class EmailConfirmationTokenTest {

    @Nested
    @DisplayName("Constructor tests")
    public class ConstructorTests {

        @Test
        @DisplayName("Default constructor creates empty object")
        public void testDefaultConstructor() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            
            assertNotNull(token);
            assertNull(token.getId());
            assertNull(token.getUserId());
            assertNull(token.getTokenHash());
            assertNull(token.getCreatedAt());
            assertNull(token.getExpiresAt());
            assertNull(token.getLastUsedAt());
        }

        @Test
        @DisplayName("Parameterized constructor initializes all required fields correctly")
        public void testParameterizedConstructor() {
            UUID userId = UUID.randomUUID();
            String tokenHash = "test-hash-value";
            Instant expiresAt = Instant.now().plus(Duration.ofHours(24));

            EmailConfirmationToken token = new EmailConfirmationToken(userId, tokenHash, expiresAt);

            assertNotNull(token.getId());
            assertEquals(userId, token.getUserId());
            assertEquals(tokenHash, token.getTokenHash());
            assertEquals(expiresAt, token.getExpiresAt());
            assertNotNull(token.getCreatedAt());
            assertNull(token.getLastUsedAt());
        }

        @Test
        @DisplayName("Parameterized constructor auto-generates unique UUID for id")
        public void testParameterizedConstructorGeneratesUniqueId() {
            UUID userId = UUID.randomUUID();
            String tokenHash = "test-hash";
            Instant expiresAt = Instant.now().plus(Duration.ofHours(24));

            EmailConfirmationToken token1 = new EmailConfirmationToken(userId, tokenHash, expiresAt);
            EmailConfirmationToken token2 = new EmailConfirmationToken(userId, tokenHash, expiresAt);

            assertNotNull(token1.getId());
            assertNotNull(token2.getId());
            assertNotEquals(token1.getId(), token2.getId());
        }

        @Test
        @DisplayName("Parameterized constructor sets createdAt to current time")
        public void testParameterizedConstructorSetsCreatedAt() {
            UUID userId = UUID.randomUUID();
            String tokenHash = "test-hash";
            Instant expiresAt = Instant.now().plus(Duration.ofHours(24));
            Instant beforeCreation = Instant.now();

            EmailConfirmationToken token = new EmailConfirmationToken(userId, tokenHash, expiresAt);

            Instant afterCreation = Instant.now();

            assertNotNull(token.getCreatedAt());
            assertFalse(token.getCreatedAt().isBefore(beforeCreation));
            assertFalse(token.getCreatedAt().isAfter(afterCreation));
        }
    }

    @Nested
    @DisplayName("isExpired() method tests")
    public class IsExpiredTests {

        @Test
        @DisplayName("isExpired returns true when current time is after expiresAt")
        public void testIsExpiredReturnsTrueWhenExpired() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            token.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));

            assertTrue(token.isExpired());
        }

        @Test
        @DisplayName("isExpired returns false when current time is before expiresAt")
        public void testIsExpiredReturnsFalseWhenNotExpired() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(1)));

            assertFalse(token.isExpired());
        }

        @Test
        @DisplayName("isExpired returns true when token expired hours ago")
        public void testIsExpiredReturnsTrueForOldToken() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            token.setExpiresAt(Instant.now().minus(Duration.ofHours(24)));

            assertTrue(token.isExpired());
        }

        @Test
        @DisplayName("isExpired returns false when token expires in the future")
        public void testIsExpiredReturnsFalseForFutureExpiration() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            token.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));

            assertFalse(token.isExpired());
        }
    }

    @Nested
    @DisplayName("isUsed() method tests")
    public class IsUsedTests {

        @Test
        @DisplayName("isUsed returns false when lastUsedAt is null")
        public void testIsUsedReturnsFalseWhenNotUsed() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            token.setLastUsedAt(null);

            assertFalse(token.isUsed());
        }

        @Test
        @DisplayName("isUsed returns true when lastUsedAt is set")
        public void testIsUsedReturnsTrueWhenUsed() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            token.setLastUsedAt(Instant.now());

            assertTrue(token.isUsed());
        }

        @Test
        @DisplayName("isUsed returns true when lastUsedAt is set to past time")
        public void testIsUsedReturnsTrueWhenUsedInPast() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            token.setLastUsedAt(Instant.now().minus(Duration.ofHours(1)));

            assertTrue(token.isUsed());
        }

        @Test
        @DisplayName("isUsed returns false by default for new token created with parameterized constructor")
        public void testIsUsedReturnsFalseForNewToken() {
            UUID userId = UUID.randomUUID();
            String tokenHash = "test-hash";
            Instant expiresAt = Instant.now().plus(Duration.ofHours(24));

            EmailConfirmationToken token = new EmailConfirmationToken(userId, tokenHash, expiresAt);

            assertFalse(token.isUsed());
        }
    }

    @Nested
    @DisplayName("Getter and Setter tests")
    public class GetterSetterTests {

        @Test
        @DisplayName("getId and setId work correctly")
        public void testGetSetId() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            UUID id = UUID.randomUUID();

            token.setId(id);

            assertEquals(id, token.getId());
        }

        @Test
        @DisplayName("getUserId and setUserId work correctly")
        public void testGetSetUserId() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            UUID userId = UUID.randomUUID();

            token.setUserId(userId);

            assertEquals(userId, token.getUserId());
        }

        @Test
        @DisplayName("getTokenHash and setTokenHash work correctly")
        public void testGetSetTokenHash() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            String tokenHash = "test-token-hash-12345";

            token.setTokenHash(tokenHash);

            assertEquals(tokenHash, token.getTokenHash());
        }

        @Test
        @DisplayName("getCreatedAt and setCreatedAt work correctly")
        public void testGetSetCreatedAt() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            Instant createdAt = Instant.now();

            token.setCreatedAt(createdAt);

            assertEquals(createdAt, token.getCreatedAt());
        }

        @Test
        @DisplayName("getExpiresAt and setExpiresAt work correctly")
        public void testGetSetExpiresAt() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            Instant expiresAt = Instant.now().plus(Duration.ofHours(24));

            token.setExpiresAt(expiresAt);

            assertEquals(expiresAt, token.getExpiresAt());
        }

        @Test
        @DisplayName("getLastUsedAt and setLastUsedAt work correctly")
        public void testGetSetLastUsedAt() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            Instant lastUsedAt = Instant.now();

            token.setLastUsedAt(lastUsedAt);

            assertEquals(lastUsedAt, token.getLastUsedAt());
        }
    }

    @Nested
    @DisplayName("Business logic integration tests")
    public class BusinessLogicIntegrationTests {

        @Test
        @DisplayName("Token is valid when not expired and not used")
        public void testTokenIsValidWhenNotExpiredAndNotUsed() {
            UUID userId = UUID.randomUUID();
            String tokenHash = "test-hash";
            Instant expiresAt = Instant.now().plus(Duration.ofHours(24));

            EmailConfirmationToken token = new EmailConfirmationToken(userId, tokenHash, expiresAt);

            assertFalse(token.isExpired());
            assertFalse(token.isUsed());
        }

        @Test
        @DisplayName("Token is invalid when expired even if not used")
        public void testTokenIsInvalidWhenExpiredAndNotUsed() {
            UUID userId = UUID.randomUUID();
            String tokenHash = "test-hash";
            Instant expiresAt = Instant.now().minus(Duration.ofHours(24));

            EmailConfirmationToken token = new EmailConfirmationToken(userId, tokenHash, expiresAt);

            assertTrue(token.isExpired());
            assertFalse(token.isUsed());
        }

        @Test
        @DisplayName("Token is used when lastUsedAt is set even if not expired")
        public void testTokenIsUsedWhenLastUsedAtSetAndNotExpired() {
            UUID userId = UUID.randomUUID();
            String tokenHash = "test-hash";
            Instant expiresAt = Instant.now().plus(Duration.ofHours(24));

            EmailConfirmationToken token = new EmailConfirmationToken(userId, tokenHash, expiresAt);
            token.setLastUsedAt(Instant.now());

            assertFalse(token.isExpired());
            assertTrue(token.isUsed());
        }

        @Test
        @DisplayName("Token can be both expired and used")
        public void testTokenCanBeBothExpiredAndUsed() {
            EmailConfirmationToken token = new EmailConfirmationToken();
            token.setExpiresAt(Instant.now().minus(Duration.ofHours(1)));
            token.setLastUsedAt(Instant.now().minus(Duration.ofMinutes(30)));

            assertTrue(token.isExpired());
            assertTrue(token.isUsed());
        }
    }
}
