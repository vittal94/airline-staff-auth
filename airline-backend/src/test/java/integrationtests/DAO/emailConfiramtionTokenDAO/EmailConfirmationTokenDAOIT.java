package integrationtests.DAO.emailConfiramtionTokenDAO;

import com.airline.airlinebackend.dao.EmailConfirmationTokenDAO;
import com.airline.airlinebackend.model.EmailConfirmationToken;
import integrationtests.DAO.AbstractDAOIT;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static integrationtests.DAO.emailConfiramtionTokenDAO.EmailConfirmationTestDataBuilder.aEmailConfirmationToken;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Tag("integration")
@DisplayName("EmailConfirmationTokenDAO integration tests")
public class EmailConfirmationTokenDAOIT extends AbstractDAOIT {
    private EmailConfirmationTokenDAO emailConfirmationTokenDAO;

    @BeforeEach
    void setup() {
        emailConfirmationTokenDAO = new EmailConfirmationTokenDAO();
    }

    private UUID insertUser() throws SQLException {
        final String sql = """
                INSERT INTO users (id, email, password_hash, role, status)
                VALUES (gen_random_uuid(), ?, '$2a$10$placeholder', 'ADMIN', 'ACTIVE')
                RETURNING id
                """;

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement stat = conn.prepareStatement(sql)) {
            stat.setString(1, "test-" + UUID.randomUUID() + "@mail.com");
            try (ResultSet rs = stat.executeQuery()) {
                rs.next();
                return rs.getObject("id", UUID.class);
            }
        }
    }

    private void insertTokenRow(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant createdAt,
            Instant lastUsed,
            Instant expiresAt
    ) throws SQLException {
        final String sql = """
                INSERT INTO email_confirmation_tokens (id, user_id, token_hash, created_at, last_used, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            ps.setString(3, tokenHash);
            ps.setTimestamp(4, Timestamp.from(createdAt));
            if (lastUsed == null) {
                ps.setTimestamp(5, null);
            } else {
                ps.setTimestamp(5, Timestamp.from(lastUsed));
            }
            ps.setTimestamp(6, Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
    }

    private Optional<EmailConfirmationToken> findTokenById(UUID tokenId) throws SQLException {
        final String sql = """
                SELECT id, user_id, token_hash, created_at, last_used, expires_at
                FROM email_confirmation_tokens
                WHERE id = ?
                """;

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tokenId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                EmailConfirmationToken token = new EmailConfirmationToken();
                token.setId(rs.getObject("id", UUID.class));
                token.setUserId(rs.getObject("user_id", UUID.class));
                token.setTokenHash(rs.getString("token_hash"));
                token.setCreatedAt(rs.getTimestamp("created_at").toInstant());

                Timestamp lastUsedTimestamp = rs.getTimestamp("last_used");
                token.setLastUsedAt(lastUsedTimestamp == null ? null : lastUsedTimestamp.toInstant());
                token.setExpiresAt(rs.getTimestamp("expires_at").toInstant());
                return Optional.of(token);
            }
        }
    }

    private Optional<Instant> findLastUsedByTokenId(UUID tokenId) throws SQLException {
        final String sql = "SELECT last_used FROM email_confirmation_tokens WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tokenId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp timestamp = rs.getTimestamp("last_used");
                return Optional.ofNullable(timestamp).map(Timestamp::toInstant);
            }
        }
    }

    private int countTokensByUserId(UUID userId) throws SQLException {
        final String sql = "SELECT COUNT(*) FROM email_confirmation_tokens WHERE user_id = ?";

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private boolean tokenExists(UUID tokenId) throws SQLException {
        final String sql = "SELECT COUNT(*) FROM email_confirmation_tokens WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tokenId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private void deleteUser(UUID userId) throws SQLException {
        final String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        }
    }

    private void assertSqlState(Throwable throwable, String expectedState) {
        assertThat(throwable)
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(SQLException.class);

        SQLException sqlException = (SQLException) throwable.getCause();
        assertThat(sqlException.getSQLState()).isEqualTo(expectedState);
    }

    @Nested
    @DisplayName("save() tests")
    class SaveTests {

        @Test
        @DisplayName("save_validToken_persistsAllExpectedColumns")
        void save_validToken_persistsAllExpectedColumns() throws SQLException {
            // Arrange
            UUID userId = insertUser();
            Instant createdAt = Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MILLIS);
            Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);
            EmailConfirmationToken token = aEmailConfirmationToken()
                    .withUserId(userId)
                    .withCreatedAt(createdAt)
                    .withExpiresAt(expiresAt)
                    .build();

            // Act
            EmailConfirmationToken saved = emailConfirmationTokenDAO.save(token);
            Optional<EmailConfirmationToken> loadedFromDb = findTokenById(token.getId());

            // Assert
            assertThat(saved).isSameAs(token);
            assertThat(loadedFromDb).isPresent();

            EmailConfirmationToken persisted = loadedFromDb.orElseThrow();
            assertSoftly(softly -> {
                softly.assertThat(persisted.getId()).isEqualTo(token.getId());
                softly.assertThat(persisted.getUserId()).isEqualTo(userId);
                softly.assertThat(persisted.getTokenHash()).isEqualTo(token.getTokenHash());
                softly.assertThat(persisted.getCreatedAt()).isCloseTo(createdAt, within(1, ChronoUnit.SECONDS));
                softly.assertThat(persisted.getExpiresAt()).isCloseTo(expiresAt, within(1, ChronoUnit.SECONDS));
                softly.assertThat(persisted.getLastUsedAt()).isNull();
            });
        }

        @Test
        @DisplayName("save_duplicateId_throwsPkConstraintViolation")
        void save_duplicateId_throwsPkConstraintViolation() {
            // Arrange
            UUID duplicateId = UUID.randomUUID();
            EmailConfirmationToken first = aEmailConfirmationToken().withId(duplicateId).build();
            EmailConfirmationToken second = aEmailConfirmationToken().withId(duplicateId).build();
            emailConfirmationTokenDAO.save(first);

            // Act & Assert
            assertThatThrownBy(() -> emailConfirmationTokenDAO.save(second))
                    .satisfies(ex -> assertSqlState(ex, "23505"));
        }

        @Test
        @DisplayName("save_duplicateTokenHash_throwsUniqueConstraintViolation")
        void save_duplicateTokenHash_throwsUniqueConstraintViolation() {
            // Arrange
            String tokenHash = UUID.randomUUID().toString();
            EmailConfirmationToken first = aEmailConfirmationToken().withTokenHash(tokenHash).build();
            EmailConfirmationToken second = aEmailConfirmationToken().withTokenHash(tokenHash).build();
            emailConfirmationTokenDAO.save(first);

            // Act & Assert
            assertThatThrownBy(() -> emailConfirmationTokenDAO.save(second))
                    .satisfies(ex -> assertSqlState(ex, "23505"));
        }

        @Test
        @DisplayName("save_nullId_throwsNotNullConstraintViolation")
        void save_nullId_throwsNotNullConstraintViolation() {
            // Arrange
            EmailConfirmationToken token = aEmailConfirmationToken().withId(null).build();

            // Act & Assert
            assertThatThrownBy(() -> emailConfirmationTokenDAO.save(token))
                    .satisfies(ex -> assertSqlState(ex, "23502"));
        }

        @Test
        @DisplayName("save_nullTokenHash_throwsNotNullConstraintViolation")
        void save_nullTokenHash_throwsNotNullConstraintViolation() {
            // Arrange
            EmailConfirmationToken token = aEmailConfirmationToken().withTokenHash(null).build();

            // Act & Assert
            assertThatThrownBy(() -> emailConfirmationTokenDAO.save(token))
                    .satisfies(ex -> assertSqlState(ex, "23502"));
        }

        @Test
        @Disabled("Bug: save() throws NullPointerException via Timestamp.from(createdAt) before DB NOT NULL constraint")
        @DisplayName("save_nullCreatedAt_throwsNotNullConstraintViolation")
        void save_nullCreatedAt_throwsNotNullConstraintViolation() {
            // Arrange
            EmailConfirmationToken token = aEmailConfirmationToken().withCreatedAt(null).build();

            // Act & Assert
            assertThatThrownBy(() -> emailConfirmationTokenDAO.save(token))
                    .satisfies(ex -> assertSqlState(ex, "23502"));
        }

        @Test
        @Disabled("Bug: save() throws NullPointerException via Timestamp.from(expiresAt) before DB NOT NULL constraint")
        @DisplayName("save_nullExpiresAt_throwsNotNullConstraintViolation")
        void save_nullExpiresAt_throwsNotNullConstraintViolation() {
            // Arrange
            EmailConfirmationToken token = aEmailConfirmationToken().withExpiresAt(null).build();

            // Act & Assert
            assertThatThrownBy(() -> emailConfirmationTokenDAO.save(token))
                    .satisfies(ex -> assertSqlState(ex, "23502"));
        }

        @Test
        @DisplayName("save_nonExistingUserId_throwsForeignKeyConstraintViolation")
        void save_nonExistingUserId_throwsForeignKeyConstraintViolation() {
            // Arrange
            EmailConfirmationToken token = aEmailConfirmationToken().withUserId(UUID.randomUUID()).build();

            // Act & Assert
            assertThatThrownBy(() -> emailConfirmationTokenDAO.save(token))
                    .satisfies(ex -> assertSqlState(ex, "23503"));
        }

        @Test
        @DisplayName("save_nullUserId_succeedsBecauseColumnIsNullable")
        void save_nullUserId_succeedsBecauseColumnIsNullable() throws SQLException {
            // Arrange
            EmailConfirmationToken token = aEmailConfirmationToken().withUserId(null).build();

            // Act
            assertThatCode(() -> emailConfirmationTokenDAO.save(token)).doesNotThrowAnyException();
            Optional<EmailConfirmationToken> saved = findTokenById(token.getId());

            // Assert
            assertThat(saved).isPresent();
            assertThat(saved.orElseThrow().getUserId()).isNull();
        }

        @Test
        @DisplayName("save_tokenHashAtMaxLength255_succeeds")
        void save_tokenHashAtMaxLength255_succeeds() {
            // Arrange
            String maxTokenHash = "a".repeat(255);
            EmailConfirmationToken token = aEmailConfirmationToken().withTokenHash(maxTokenHash).build();

            // Act & Assert
            assertThatCode(() -> emailConfirmationTokenDAO.save(token)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("save_tokenHashOverMaxLength255_throwsConstraintViolation")
        void save_tokenHashOverMaxLength255_throwsConstraintViolation() {
            // Arrange
            String tooLongTokenHash = "a".repeat(256);
            EmailConfirmationToken token = aEmailConfirmationToken().withTokenHash(tooLongTokenHash).build();

            // Act & Assert
            assertThatThrownBy(() -> emailConfirmationTokenDAO.save(token))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class);
        }
    }

    @Nested
    @DisplayName("findByTokenHash() tests")
    class FindByTokenHashTests {

        @Test
        @DisplayName("findByTokenHash_existingHash_returnsToken")
        void findByTokenHash_existingHash_returnsToken() throws SQLException {
            // Arrange
            UUID userId = insertUser();
            UUID tokenId = UUID.randomUUID();
            String tokenHash = UUID.randomUUID().toString();
            Instant createdAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS);
            Instant lastUsedAt = Instant.now().minusSeconds(20).truncatedTo(ChronoUnit.MILLIS);
            Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);

            insertTokenRow(tokenId, userId, tokenHash, createdAt, lastUsedAt, expiresAt);

            // Act
            Optional<EmailConfirmationToken> result = emailConfirmationTokenDAO.findByTokenHash(tokenHash);

            // Assert
            assertThat(result).isPresent();
            EmailConfirmationToken found = result.orElseThrow();

            assertSoftly(softly -> {
                softly.assertThat(found.getId()).isEqualTo(tokenId);
                softly.assertThat(found.getUserId()).isEqualTo(userId);
                softly.assertThat(found.getTokenHash()).isEqualTo(tokenHash);
                softly.assertThat(found.getCreatedAt()).isCloseTo(createdAt, within(1, ChronoUnit.SECONDS));
                softly.assertThat(found.getLastUsedAt()).isCloseTo(lastUsedAt, within(1, ChronoUnit.SECONDS));
                softly.assertThat(found.getExpiresAt()).isCloseTo(expiresAt, within(1, ChronoUnit.SECONDS));
            });
        }

        @Test
        @DisplayName("findByTokenHash_unknownHash_returnsEmpty")
        void findByTokenHash_unknownHash_returnsEmpty() {
            // Arrange
            String tokenHash = UUID.randomUUID().toString();

            // Act
            Optional<EmailConfirmationToken> result = emailConfirmationTokenDAO.findByTokenHash(tokenHash);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findByTokenHash_nullHash_returnsEmpty")
        void findByTokenHash_nullHash_returnsEmpty() {
            // Act
            Optional<EmailConfirmationToken> result = emailConfirmationTokenDAO.findByTokenHash(null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @Disabled("Bug: mapResultSetToToken() calls toInstant() on nullable last_used and throws NullPointerException")
        @DisplayName("findByTokenHash_nullLastUsed_returnsTokenWithNullLastUsed")
        void findByTokenHash_nullLastUsed_returnsTokenWithNullLastUsed() throws SQLException {
            // Arrange
            UUID userId = insertUser();
            UUID tokenId = UUID.randomUUID();
            String tokenHash = UUID.randomUUID().toString();
            Instant createdAt = Instant.now().minusSeconds(60);
            Instant expiresAt = Instant.now().plusSeconds(3600);
            insertTokenRow(tokenId, userId, tokenHash, createdAt, null, expiresAt);

            // Act
            Optional<EmailConfirmationToken> result = emailConfirmationTokenDAO.findByTokenHash(tokenHash);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getLastUsedAt()).isNull();
        }
    }


    @Nested
    @DisplayName("markUsed() tests")
    class MarkUsedTests {

        @Test
        @DisplayName("markUsed_existingToken_setsLastUsedTimestamp")
        void markUsed_existingToken_setsLastUsedTimestamp() throws SQLException {
            // Arrange
            UUID tokenId = UUID.randomUUID();
            insertTokenRow(
                    tokenId,
                    null,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().plusSeconds(1200)
            );
            Instant beforeMarkUsed = Instant.now().minusSeconds(1);

            // Act
            emailConfirmationTokenDAO.markUsed(tokenId);
            Optional<Instant> lastUsed = findLastUsedByTokenId(tokenId);

            // Assert
            assertThat(lastUsed).isPresent();
            assertThat(lastUsed.orElseThrow()).isAfter(beforeMarkUsed);
        }

        @Test
        @DisplayName("markUsed_updatesOnlyRequestedToken")
        void markUsed_updatesOnlyRequestedToken() throws SQLException {
            // Arrange
            UUID targetTokenId = UUID.randomUUID();
            UUID untouchedTokenId = UUID.randomUUID();
            Instant untouchedLastUsedAt = Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.MILLIS);

            insertTokenRow(
                    targetTokenId,
                    null,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().plusSeconds(1200)
            );
            insertTokenRow(
                    untouchedTokenId,
                    null,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    untouchedLastUsedAt,
                    Instant.now().plusSeconds(1200)
            );

            // Act
            emailConfirmationTokenDAO.markUsed(targetTokenId);
            Optional<Instant> targetLastUsed = findLastUsedByTokenId(targetTokenId);
            Optional<Instant> untouchedLastUsed = findLastUsedByTokenId(untouchedTokenId);

            // Assert
            assertThat(targetLastUsed).isPresent();
            assertThat(untouchedLastUsed).isPresent();
            assertThat(untouchedLastUsed.orElseThrow())
                    .isCloseTo(untouchedLastUsedAt, within(1, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("markUsed_unknownTokenId_doesNotThrow")
        void markUsed_unknownTokenId_doesNotThrow() {
            // Arrange
            UUID unknownId = UUID.randomUUID();

            // Act & Assert
            assertThatCode(() -> emailConfirmationTokenDAO.markUsed(unknownId)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("deleteByUserId() tests")
    class DeleteByUserIdTests {

        @Test
        @DisplayName("deleteByUserId_existingUserId_deletesOnlyThatUserTokens")
        void deleteByUserId_existingUserId_deletesOnlyThatUserTokens() throws SQLException {
            // Arrange
            UUID userIdToDelete = insertUser();
            UUID anotherUserId = insertUser();

            insertTokenRow(
                    UUID.randomUUID(),
                    userIdToDelete,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30),
                    Instant.now().plusSeconds(300)
            );
            insertTokenRow(
                    UUID.randomUUID(),
                    userIdToDelete,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30),
                    Instant.now().plusSeconds(300)
            );
            insertTokenRow(
                    UUID.randomUUID(),
                    anotherUserId,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30),
                    Instant.now().plusSeconds(300)
            );

            // Act
            emailConfirmationTokenDAO.deleteByUserId(userIdToDelete);

            // Assert
            assertThat(countTokensByUserId(userIdToDelete)).isZero();
            assertThat(countTokensByUserId(anotherUserId)).isEqualTo(1);
        }

        @Test
        @DisplayName("deleteByUserId_unknownUserId_doesNotThrow")
        void deleteByUserId_unknownUserId_doesNotThrow() {
            // Arrange
            UUID unknownUserId = UUID.randomUUID();

            // Act & Assert
            assertThatCode(() -> emailConfirmationTokenDAO.deleteByUserId(unknownUserId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("deleteByUserId_nullUserId_doesNotThrow")
        void deleteByUserId_nullUserId_doesNotThrow() {
            // Act & Assert
            assertThatCode(() -> emailConfirmationTokenDAO.deleteByUserId(null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("deleteExpired() tests")
    class DeleteExpiredTests {

        @Test
        @DisplayName("deleteExpired_mixedTokens_deletesOnlyExpiredAndReturnsCount")
        void deleteExpired_mixedTokens_deletesOnlyExpiredAndReturnsCount() throws SQLException {
            // Arrange
            UUID expiredOne = UUID.randomUUID();
            UUID expiredTwo = UUID.randomUUID();
            UUID expiredThree = UUID.randomUUID();
            UUID validOne = UUID.randomUUID();
            UUID validTwo = UUID.randomUUID();

            insertTokenRow(
                    expiredOne,
                    null,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().minusSeconds(10)
            );
            insertTokenRow(
                    expiredTwo,
                    null,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().minusSeconds(20)
            );
            insertTokenRow(
                    expiredThree,
                    null,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().minusSeconds(30)
            );
            insertTokenRow(
                    validOne,
                    null,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().plusSeconds(300)
            );
            insertTokenRow(
                    validTwo,
                    null,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().plusSeconds(600)
            );

            // Act
            int deletedCount = emailConfirmationTokenDAO.deleteExpired();

            // Assert
            assertThat(deletedCount).isEqualTo(3);
            assertThat(tokenExists(expiredOne)).isFalse();
            assertThat(tokenExists(expiredTwo)).isFalse();
            assertThat(tokenExists(expiredThree)).isFalse();
            assertThat(tokenExists(validOne)).isTrue();
            assertThat(tokenExists(validTwo)).isTrue();
        }

        @Test
        @DisplayName("deleteExpired_withoutExpiredTokens_returnsZero")
        void deleteExpired_withoutExpiredTokens_returnsZero() throws SQLException {
            // Arrange
            UUID validOne = UUID.randomUUID();
            UUID validTwo = UUID.randomUUID();
            insertTokenRow(
                    validOne,
                    null,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().plusSeconds(300)
            );
            insertTokenRow(
                    validTwo,
                    null,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().plusSeconds(600)
            );

            // Act
            int deletedCount = emailConfirmationTokenDAO.deleteExpired();

            // Assert
            assertThat(deletedCount).isZero();
            assertThat(tokenExists(validOne)).isTrue();
            assertThat(tokenExists(validTwo)).isTrue();
        }
    }

    @Nested
    @DisplayName("schema contract tests")
    class SchemaContractTests {

        @Test
        @DisplayName("deleteParentUser_tokensAreCascadeDeleted")
        void deleteParentUser_tokensAreCascadeDeleted() throws SQLException {
            // Arrange
            UUID userId = insertUser();
            insertTokenRow(
                    UUID.randomUUID(),
                    userId,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().plusSeconds(600)
            );
            insertTokenRow(
                    UUID.randomUUID(),
                    userId,
                    UUID.randomUUID().toString(),
                    Instant.now().minusSeconds(120),
                    null,
                    Instant.now().plusSeconds(600)
            );
            assertThat(countTokensByUserId(userId)).isEqualTo(2);

            // Act
            deleteUser(userId);

            // Assert
            assertThat(countTokensByUserId(userId)).isZero();
        }
    }

}
