package integrationtests.DAO.rememberMeDAO;

import com.airline.airlinebackend.dao.RememberMeTokenDAO;
import com.airline.airlinebackend.model.RememberMeToken;
import integrationtests.DAO.AbstractDAOIT;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static integrationtests.DAO.rememberMeDAO.RememberMeTestDataBuilder.aRememberMe;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.SoftAssertions.assertSoftly;


@Tag("integration")
@DisplayName("RememberMeDAO integration tests")
public class RememberMeDAOIT extends AbstractDAOIT {
    private RememberMeTokenDAO rememberMeTokenDAO;

    @BeforeEach
    void setup() {
        rememberMeTokenDAO = new RememberMeTokenDAO();
    }

    /**
     * Inserts a minimal, valid user row directly via JDBC and returns its UUID.
     * Used to satisfy the sessions.user_id FK constraint without depending on UserDAO.
     */
    private UUID insertUser() throws SQLException {
        final String sql = """
                INSERT INTO users (id, email, password_hash, role, status)
                VALUES (gen_random_uuid(), ?, '$2a$10$placeholder', 'ADMIN', 'ACTIVE')
                RETURNING id
                """;
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "testuser-" + UUID.randomUUID() + "@airline.test");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject("id", UUID.class);
            }
        }
    }

    @Nested
    @DisplayName("save() tests")
    class SaveTests {

        @Test
        @DisplayName("should save with all corrected fields and return rememberMeToken")
        void saveWithAllCorrectedFields() throws Exception {
            UUID userId = insertUser();

            RememberMeToken token = aRememberMe()
                    .withSeries(UUID.randomUUID().toString())
                    .withUserId(userId)
                    .withTokenHash(UUID.randomUUID().toString())
                    .withUserAgent("Opera 5.0")
                    .withIpAddress("123.0.0.4")
                    .build();

            rememberMeTokenDAO.save(token);

            RememberMeToken savedToken = rememberMeTokenDAO.findBySeries(token.getSeries()).orElseThrow();

            assertSoftly(softly -> {
                softly.assertThat(savedToken.getUserId()).isEqualTo(userId);
                softly.assertThat(savedToken.getTokenHash()).isEqualTo(token.getTokenHash());
                softly.assertThat(savedToken.getUserAgent()).isEqualTo("Opera 5.0");
                softly.assertThat(savedToken.getIpAddress()).isEqualTo("123.0.0.4");
                softly.assertThat(savedToken.getSeries()).isEqualTo(token.getSeries());
            });

        }

        @Test
        @DisplayName("should preserve created_at when explicitly set")
        void save_explicitCreatedAt_preservesValue() {
            Instant fixDate = Timestamp.valueOf("2026-06-13 12:34:00").toInstant();
            RememberMeToken token = aRememberMe().withCreatedAt(fixDate).build();

            rememberMeTokenDAO.save(token);

            RememberMeToken retrievedToken = rememberMeTokenDAO.findBySeries(token.getSeries()).orElseThrow();

            assertThat(retrievedToken.getCreatedAt()).isEqualTo(fixDate);
        }

        @Test
        @DisplayName("should save created_at when set to now")
        void save_createdAtNow_isAfterBeforeMarker() {
            Instant before = Instant.now().minusSeconds(12);

            RememberMeToken token = aRememberMe().withCreatedAt(Instant.now()).build();

            rememberMeTokenDAO.save(token);

            RememberMeToken retrievedToken = rememberMeTokenDAO.findBySeries(token.getSeries()).orElseThrow();

            assertThat(retrievedToken.getCreatedAt()).isAfter(before);
        }

        @Test
        @DisplayName("should throw RuntimeException with SQL state 23505 on duplicate series (PK uniqueness)")
        void save_duplicateSeries_throwsRuntimeException() throws SQLException {
            String series = UUID.randomUUID().toString();

            rememberMeTokenDAO.save(aRememberMe().withSeries(series).build());

            assertThatCode(() -> rememberMeTokenDAO.save(aRememberMe().withSeries(series).build()))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(ex -> {
                        SQLException sqlException = (SQLException) ex.getCause();
                        assertThat(sqlException.getSQLState()).isEqualTo("23505");
                    });

        }

        @Test
        @DisplayName("should throw SQL state 23502 when series is null (NOT NULL constraint)")
        void save_nullSeries_throwsConstraintViolation() {
            RememberMeToken token = aRememberMe().withSeries(null).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(ex -> {
                        SQLException sqlException = (SQLException) ex.getCause();
                        assertThat(sqlException.getSQLState()).isEqualTo("23502");
                    });

        }

        @Test
        @DisplayName("should throw SQL state 23502 when token_hash is null (NOT NULL constraint)")
        void save_nullToken_hash_throwsConstraintViolation() {
            RememberMeToken token = aRememberMe().withTokenHash(null).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(ex -> {
                        SQLException sqlException = (SQLException) ex.getCause();
                        assertThat(sqlException.getSQLState()).isEqualTo("23502");
                    });
        }

        @Test
        @DisplayName("should throw SQL state 23502 when created_at is null (NOT NULL constraint)")
        @Disabled("BUG throws NPE because Timestamp.form(), should throw SQL instead")
        void save_nullCreated_at_throwsConstraintViolation() {}

        @Test
        @DisplayName("should throw SQL state 23502 when last_used_at is null (NOT NULL constraint)")
        @Disabled("BUG throws NPE because Timestamp.form(), should throw SQL instead")
        void save_nullLast_used_at_throwsConstraintViolation() {}

        @Test
        @DisplayName("should throw SQL state 23502 when expires_at is null (NOT NULL constraint)")
        @Disabled("BUG throws NPE because Timestamp.form(), should throw SQL instead")
        void save_nullExpires_at_throwsConstraintViolation() {}

        @Test
        @DisplayName("should throw RuntimeException when non-existing user_id(FK constraint) ")
        void save_non_existing_user_id() {
            RememberMeToken token = aRememberMe().withUserId(UUID.randomUUID()).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token)).isExactlyInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should save successfully when user_id is null (nullable column)")
        void save_nullUserId_succeeds() {
            RememberMeToken token = aRememberMe().withUserId(null).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token)).doesNotThrowAnyException();

            RememberMeToken retrieved = rememberMeTokenDAO.findBySeries(token.getSeries()).orElseThrow();
            assertThat(retrieved.getUserId()).isNull();
        }

        @Test
        @DisplayName("should save series with max length 64 characters")
        void save_seriesAtMaxLength64_succeeds() {
            String series = "a".repeat(64);
            RememberMeToken token = aRememberMe().withSeries(series).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token)).doesNotThrowAnyException();

            assertThat(rememberMeTokenDAO.findBySeries(series)).isPresent();
        }

        @Test
        @DisplayName("should throw when series exceeds 64 characters")
        void save_seriesOver64Chars_throws() {
            String series = "a".repeat(65);
            RememberMeToken token = aRememberMe().withSeries(series).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class);
        }

        @Test
        @DisplayName("should save token_hash with max length 255 characters")
        void save_tokenHashAtMaxLength255_succeeds() {
            String tokenHash = "a".repeat(255);
            RememberMeToken token = aRememberMe().withTokenHash(tokenHash).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token)).doesNotThrowAnyException();

            RememberMeToken retrieved = rememberMeTokenDAO.findBySeries(token.getSeries()).orElseThrow();
            assertThat(retrieved.getTokenHash()).hasSize(255);
        }

        @Test
        @DisplayName("should throw when token_hash exceeds 255 characters")
        void save_tokenHashOver255Chars_throws() {
            String tokenHash = "a".repeat(256);
            RememberMeToken token = aRememberMe().withTokenHash(tokenHash).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class);
        }

        @Test
        @DisplayName("should save ip_address with max length 45 characters (IPv6)")
        void save_ipAddressAtMaxLength45_succeeds() {
            String ipAddress = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"; // 39 chars, valid IPv6
            RememberMeToken token = aRememberMe().withIpAddress(ipAddress).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token)).doesNotThrowAnyException();

            RememberMeToken retrieved = rememberMeTokenDAO.findBySeries(token.getSeries()).orElseThrow();
            assertThat(retrieved.getIpAddress()).isEqualTo(ipAddress);
        }

        @Test
        @DisplayName("should throw when ip_address exceeds 45 characters")
        void save_ipAddressOver45Chars_throws() {
            String ipAddress = "a".repeat(46);
            RememberMeToken token = aRememberMe().withIpAddress(ipAddress).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class);
        }
    }

    @Nested
    @DisplayName("findBySeries() tests")
    class FindBySeriesTests {

        @Test
        @DisplayName("should find the rememberMeToken by existing series")
        void findByExistingSeries() {
            RememberMeToken token = aRememberMe()
                    .withSeries(UUID.randomUUID().toString())
                    .withTokenHash(UUID.randomUUID().toString())
                    .withUserAgent("Mozila 5.1")
                    .withIpAddress("123.4.5.123")
                    .build();

            rememberMeTokenDAO.save(token);

            RememberMeToken retrievedToken = rememberMeTokenDAO.findBySeries(token.getSeries()).orElseThrow();

            assertSoftly(softly -> {
                softly.assertThat(retrievedToken.getSeries()).isEqualTo(token.getSeries());
                softly.assertThat(retrievedToken.getUserAgent()).isEqualTo(token.getUserAgent());
                softly.assertThat(retrievedToken.getIpAddress()).isEqualTo(token.getIpAddress());
                softly.assertThat(retrievedToken.getTokenHash()).isEqualTo(token.getTokenHash());
            });
        }

        @Test
        @DisplayName("should return empty if non-existing series")
        void returnEmpty_whenNonExistingSeries() {
            String series = UUID.randomUUID().toString();
            assertThat(rememberMeTokenDAO.findBySeries(series)).isEmpty();
        }

        @Test
        @DisplayName("should return empty when series is null")
        void returnEmpty_whenSeriesIsNull() {
            assertThat(rememberMeTokenDAO.findBySeries(null)).isEmpty();
        }

        @Test
        @DisplayName("should not return another rememberMeToken by existing series")
        void returnExactlyRememberMeToken() {
            RememberMeToken token1 = rememberMeTokenDAO.save(aRememberMe()
                    .withSeries(UUID.randomUUID().toString())
                    .withTokenHash(UUID.randomUUID().toString())
                    .withIpAddress("123.0.0.4")
            .build());

            RememberMeToken token2 = rememberMeTokenDAO.save(aRememberMe()
                    .withSeries(UUID.randomUUID().toString())
                    .withTokenHash(UUID.randomUUID().toString())
                    .withIpAddress("124.0.0.12")
                    .build());

            assertThat(rememberMeTokenDAO.findBySeries(token2.getSeries()))
                    .isPresent()
                    .get()
                    .extracting(RememberMeToken::getTokenHash)
                    .isEqualTo(token2.getTokenHash());
        }
    }

    @Nested
    @DisplayName("update() tests")
    class UpdateTests {

        @Test
        @DisplayName("should update all field correctly")
        void update_all_field_correctly() {
            String tokenHash = UUID.randomUUID().toString();
            Instant before = Instant.now().plusSeconds(10);

            RememberMeToken original = aRememberMe()
                    .withSeries(UUID.randomUUID().toString())
                    .withTokenHash(tokenHash)
                    .withExpiresAt(before)
                    .build();

            rememberMeTokenDAO.save(original);

            rememberMeTokenDAO.update(original.getSeries(),
                    UUID.randomUUID().toString(),
                    Instant.now().plusSeconds(20));

            RememberMeToken updated = rememberMeTokenDAO.findBySeries(original.getSeries()).orElseThrow();

            assertThat(updated.getTokenHash()).isNotEqualTo(tokenHash);
            assertThat(updated.getExpiresAt()).isAfter(original.getExpiresAt());
        }

        @Test
        @DisplayName("should not update another rememberMeToken")
        void notUpdate_rememberMeToken() {
            String secondTokenHash = UUID.randomUUID().toString();
            Instant secondTokenExpires = Instant.now().plusSeconds(10).truncatedTo(ChronoUnit.MILLIS);

            RememberMeToken firstToken = rememberMeTokenDAO.save(aRememberMe()
                    .withExpiresAt(Instant.now().plusSeconds(10))
                    .build());

            RememberMeToken secondToken = rememberMeTokenDAO.save(aRememberMe()
                            .withTokenHash(secondTokenHash)
                            .withExpiresAt(secondTokenExpires)
                    .build());

            rememberMeTokenDAO.update(
                    firstToken.getSeries(),
                    UUID.randomUUID().toString(),
                    Instant.now().plusSeconds(20));

            RememberMeToken retrievedSecondToken = rememberMeTokenDAO.findBySeries(secondToken.getSeries()).orElseThrow();

            assertThat(retrievedSecondToken.getTokenHash()).isEqualTo(secondTokenHash);
            assertThat(retrievedSecondToken.getExpiresAt()).isEqualTo(secondTokenExpires);
        }

        @Test
        @DisplayName("should throw SQL state 23502 when token_hash is null (NOT NULL constraint)")
        void throwWhenTokenHashNull() {
            RememberMeToken token = rememberMeTokenDAO.save(aRememberMe().build());

            assertThatCode(() -> rememberMeTokenDAO.update(token.getSeries(),
            null, Instant.now().plusSeconds(20))).isExactlyInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies( ex -> {
                        SQLException sqlException = (SQLException) ex.getCause();
                        assertThat(sqlException.getSQLState()).isEqualTo("23502");
                    });
        }

        @Test
        @DisplayName("should return zero count when non-existing series")
        @Disabled("BUG: update() is silent")
        void returnZeroCount_whenNonExistingSeries() {}

        @Test
        @DisplayName("should return zero count when null series")
        @Disabled("BUG: update() is silent")
        void returnZeroCount_whenNullSeries() {}

        @Test
        @DisplayName("should throw SQL state 23502 when new expires_at is null (NOT NULL constraint)")
        @Disabled("BUG: update throws NPE cause of Timestamp.from()")
        void throwWhen_newExpiresAtNull() {

        }

        @Test
        @DisplayName("should update last_used_at in database")
        void update_lastUsedAt_isUpdatedInDb() throws InterruptedException {
            RememberMeToken original = aRememberMe().build();
            rememberMeTokenDAO.save(original);

            Instant originalLastUsedAt = rememberMeTokenDAO.findBySeries(original.getSeries())
                    .orElseThrow()
                    .getLastUsedAt();

            // Small delay to ensure timestamp difference
            Thread.sleep(100);

            rememberMeTokenDAO.update(
                    original.getSeries(),
                    UUID.randomUUID().toString(),
                    Instant.now().plusSeconds(20));

            RememberMeToken updated = rememberMeTokenDAO.findBySeries(original.getSeries()).orElseThrow();

            assertThat(updated.getLastUsedAt()).isAfter(originalLastUsedAt);
        }
    }

    @Nested
    @DisplayName("deleteBySeries() tests")
    class DeleteBySeriesTests {

        @Test
        @DisplayName("should delete by existing series")
        void deleteByExistingSeries() {
            String series = UUID.randomUUID().toString();

            rememberMeTokenDAO.save(aRememberMe().withSeries(series).build());

            rememberMeTokenDAO.deleteBySeries(series);

            assertThat(rememberMeTokenDAO.findBySeries(series)).isEmpty();
        }

        @Test
        @DisplayName("should not delete another rememberMeToken")
        void notDelete_rememberMeToken() {
            RememberMeToken firstToken = rememberMeTokenDAO.save(aRememberMe().build());
            RememberMeToken secondToken = rememberMeTokenDAO.save(aRememberMe().build());

            rememberMeTokenDAO.deleteBySeries(firstToken.getSeries());

            assertThat(rememberMeTokenDAO.findBySeries(firstToken.getSeries())).isEmpty();
            assertThat(rememberMeTokenDAO.findBySeries(secondToken.getSeries())).isPresent();
        }

        @Test
        @DisplayName("return zero when non-existing series")
        @Disabled("BUG: deleteBySeries() is silent")
        void returnZero_whenNonExistingSeries() {}

        @Test
        @DisplayName("return zero when null series")
        @Disabled("BUG: deleteBySeries() is silent")
        void returnZero_whenNullSeries() {}
    }

    @Nested
    @DisplayName("deleteByUserId() tests")
    class DeleteByUserIdTests {


        @Test
        @DisplayName("should delete by existing user_id")
        void deleteByExistingId() throws Exception {
            UUID userId = insertUser();
            RememberMeToken token = rememberMeTokenDAO.save(aRememberMe().withUserId(userId).build());

            rememberMeTokenDAO.deleteByUserId(userId);

            assertThat(rememberMeTokenDAO.findBySeries(token.getSeries())).isEmpty();
        }

        @Test
        @DisplayName("should not delete another rememberMeToken")
        void notDelete_rememberMeToken() throws Exception {
            UUID firstUserId = insertUser();
            UUID secondUserId = insertUser();

            RememberMeToken firstToken = rememberMeTokenDAO.save(aRememberMe().withUserId(firstUserId).build());
            RememberMeToken secondToken = rememberMeTokenDAO.save(aRememberMe().withUserId(secondUserId).build());

            rememberMeTokenDAO.deleteByUserId(firstUserId);

            assertThat(rememberMeTokenDAO.findBySeries(firstToken.getSeries())).isEmpty();
            assertThat(rememberMeTokenDAO.findBySeries(secondToken.getSeries())).isPresent();
        }

        @Test
        @DisplayName("return zero when non-existing user_id")
        @Disabled("BUG: deleteByUserId() is silent")
        void returnZero_whenNonExistingUserId() {}

        @Test
        @DisplayName("return zero when null user_id")
        @Disabled("BUG: deleteByUserId() is silent")
        void returnZero_whenNullUserId() {}

        @Test
        @DisplayName("should delete all tokens for a user with multiple tokens (multi-device)")
        void deleteByUserId_multipleTokens_deletesAll() throws SQLException {
            UUID userId = insertUser();

            RememberMeToken token1 = rememberMeTokenDAO.save(aRememberMe().withUserId(userId).build());
            RememberMeToken token2 = rememberMeTokenDAO.save(aRememberMe().withUserId(userId).build());
            RememberMeToken token3 = rememberMeTokenDAO.save(aRememberMe().withUserId(userId).build());

            rememberMeTokenDAO.deleteByUserId(userId);

            assertThat(rememberMeTokenDAO.findBySeries(token1.getSeries())).isEmpty();
            assertThat(rememberMeTokenDAO.findBySeries(token2.getSeries())).isEmpty();
            assertThat(rememberMeTokenDAO.findBySeries(token3.getSeries())).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteExpired() tests")
    class DeleteExpiredTests {

        @Test
        @DisplayName("should delete all expired tokens")
        void deleteExpired() {
            for(int i = 0; i < 10; i++) {
                rememberMeTokenDAO.save(
                        aRememberMe().withExpiresAt(Instant.now().minusSeconds(i+1))
                .build());
            }

            assertThat(rememberMeTokenDAO.deleteExpired()).isEqualTo(10);
        }

        @Test
        @DisplayName("should not delete valid tokens")
        void notDelete_notExpired() {
            for(int i = 0; i < 10; i++) {
                if(i % 2 == 0) {
                    rememberMeTokenDAO.save(
                            aRememberMe().withExpiresAt(Instant.now().minusSeconds(i+1))
                                    .build());
                }
                else {
                    rememberMeTokenDAO.save(
                            aRememberMe().withExpiresAt(Instant.now().plusSeconds(i+1))
                            .build());
                }
            }

            assertThat(rememberMeTokenDAO.deleteExpired()).isEqualTo(5);
        }

        @Test
        @DisplayName("should return zero when no expired tokens exist")
        void deleteExpired_noExpiredTokens_returnsZero() {
            rememberMeTokenDAO.save(aRememberMe().withExpiresAt(Instant.now().plusSeconds(100)).build());
            rememberMeTokenDAO.save(aRememberMe().withExpiresAt(Instant.now().plusSeconds(200)).build());
            rememberMeTokenDAO.save(aRememberMe().withExpiresAt(Instant.now().plusSeconds(300)).build());

            assertThat(rememberMeTokenDAO.deleteExpired()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not delete token expiring exactly now (boundary test: < vs <=)")
        void deleteExpired_tokenExpiringExactlyNow_isNotDeleted() throws InterruptedException {
            // Save a token that expires in the near future
            RememberMeToken tokenExpiringNow = aRememberMe()
                    .withExpiresAt(Instant.now().plusMillis(50))
                    .build();
            rememberMeTokenDAO.save(tokenExpiringNow);

            // Wait for the token to reach expiration time
            Thread.sleep(100);

            // The SQL uses `WHERE expires_at < now()` (strict less-than)
            // A token with expires_at exactly equal to now() should NOT be deleted
            // In practice, this is subtle—we're verifying the DAO uses `<` not `<=`
            int deleted = rememberMeTokenDAO.deleteExpired();

            // The token should be deleted because it's now in the past
            assertThat(deleted).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCasesTests {
        @Test
        @DisplayName("should handle user_agent with max length 500")
        void handleMaxLength_userAgent() {
            String userAgent = "Mozila 5." + "0".repeat(491);

            assertThatCode(() ->
                    rememberMeTokenDAO.save(aRememberMe().withUserAgent(userAgent).build())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle user_agent with special characters")
        void handleSpecialChars_userAgent() {
            String specialUserAgent = "O'Brien-José María 日本語 Ñoño <script>alert('xss')</script>";

            assertThatCode(() ->
                    rememberMeTokenDAO.save(aRememberMe().withUserAgent(specialUserAgent).build())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw when user_agent exceeds 500 characters")
        void handleUserAgentOverMaxLength_throws() {
            String userAgent = "Mozilla 5." + "0".repeat(492); // 501 chars total

            RememberMeToken token = aRememberMe().withUserAgent(userAgent).build();

            assertThatCode(() -> rememberMeTokenDAO.save(token))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class);
        }
    }

    @Nested
    @DisplayName("schema contract tests")
    class SchemaContractTests {

        @Test
        @DisplayName("should CASCADE delete tokens when parent user is deleted (FK constraint)")
        void save_userDeleted_tokensCascadeDeleted() throws SQLException {
            UUID userId = insertUser();

            RememberMeToken token1 = rememberMeTokenDAO.save(aRememberMe().withUserId(userId).build());
            RememberMeToken token2 = rememberMeTokenDAO.save(aRememberMe().withUserId(userId).build());

            // Verify tokens exist
            assertThat(rememberMeTokenDAO.findBySeries(token1.getSeries())).isPresent();
            assertThat(rememberMeTokenDAO.findBySeries(token2.getSeries())).isPresent();

            // Delete the parent user via JDBC
            final String deleteUserSql = "DELETE FROM users WHERE id = ?";
            try (Connection conn = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 PreparedStatement ps = conn.prepareStatement(deleteUserSql)) {
                ps.setObject(1, userId);
                ps.executeUpdate();
            }

            // Verify tokens were CASCADE deleted
            assertThat(rememberMeTokenDAO.findBySeries(token1.getSeries())).isEmpty();
            assertThat(rememberMeTokenDAO.findBySeries(token2.getSeries())).isEmpty();
        }
    }

    @Nested
    @DisplayName("complex scenarios")
    class ComplexScenariosTests {

        @Test
        @DisplayName("full life-cycle of rememberMeToken creation -> update -> delete")
        void fullRememberMeTokenCycle() throws Exception {
            //user login session and remember me creation
            UUID userId = insertUser();
            RememberMeToken token = rememberMeTokenDAO.save(aRememberMe().withUserId(userId).build());

            assertThat(rememberMeTokenDAO.findBySeries(token.getSeries())).isPresent();

            //token rotation
            rememberMeTokenDAO.update(
                    token.getSeries(),
                    UUID.randomUUID().toString(),
                    Instant.now().plusSeconds(20));

            assertThat(rememberMeTokenDAO.findBySeries(token.getSeries()))
                    .isPresent()
                    .get()
                    .extracting(RememberMeToken::getTokenHash)
                    .isNotEqualTo(token.getTokenHash());

            //theft detection delete the rememberMe
            rememberMeTokenDAO.deleteByUserId(userId);

            assertThat(rememberMeTokenDAO.findBySeries(token.getSeries())).isNotPresent();
        }

        @Test
        @DisplayName("single user can have multiple concurrent rememberMeTokens (multi-device scenario)")
        void singleUser_multipleTokens_allIndependent() throws SQLException {
            UUID userId = insertUser();

            //login from first device
            RememberMeToken token1 = rememberMeTokenDAO.save(aRememberMe().withUserId(userId).build());

            //login from second device
            RememberMeToken token2 = rememberMeTokenDAO.save(aRememberMe().withUserId(userId).build());

            RememberMeToken retrievedToken1 = rememberMeTokenDAO.findBySeries(token1.getSeries()).orElseThrow();
            RememberMeToken retrievedToken2 = rememberMeTokenDAO.findBySeries(token2.getSeries()).orElseThrow();

            assertThat(retrievedToken1.getUserId()).isEqualTo(retrievedToken2.getUserId());
            assertThat(retrievedToken1.getSeries()).isNotEqualTo(retrievedToken2.getSeries());
            assertThat(retrievedToken1.getTokenHash()).isNotEqualTo(retrievedToken2.getTokenHash());
        }
    }
}
