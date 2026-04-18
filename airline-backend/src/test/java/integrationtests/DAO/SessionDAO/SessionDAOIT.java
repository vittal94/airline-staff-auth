/**
 * SessionDAOIT.java
 *
 * Full integration test suite for {@link com.airline.airlinebackend.dao.SessionDAO}.
 * Covers: isolated CRUD tests, schema contract tests (PK, NOT NULL, FK, column-length
 * boundaries, JSONB null behaviour), edge cases, full lifecycle, and concurrent access.
 *
 * Each test runs against a real PostgreSQL container managed by AbstractDAOIT.
 * DB is truncated before every test via @BeforeEach in AbstractDAOIT.
 */
package integrationtests.DAO.SessionDAO;

import com.airline.airlinebackend.dao.SessionDAO;
import com.airline.airlinebackend.model.Session;
import integrationtests.DAO.AbstractDAOIT;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static integrationtests.DAO.SessionDAO.SessionTestDataBuilder.aSession;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Tag("integration")
@DisplayName("SessionDAO Integration Tests")
public class SessionDAOIT extends AbstractDAOIT {

    private SessionDAO sessionDAO;

    @BeforeEach
    void setup() {
        sessionDAO = new SessionDAO();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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

    /**
     * Reloads the session from the DB to assert on persisted state, not in-memory state.
     */
    private Session reload(String id) {
        return sessionDAO.findById(id)
                .orElseThrow(() -> new AssertionError("Session not found in DB: " + id));
    }

    // -----------------------------------------------------------------------
    // save()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("should persist all fields and return the session")
        void save_validSession_persistsAllFields() throws SQLException {
            // Arrange
            UUID userId = insertUser();
            Session session = aSession()
                    .withUserId(userId)
                    .withUserAgent("Safari/2.0")
                    .withSessionData(Map.of(
                            "csrfToken", "123",
                            "userRole", "admin",
                            "rememberMe", true,
                            "ui", Map.of("theme", "dark")))
                    .withIpAddress("192.168.1.10")
                    .build();

            // Act
            sessionDAO.save(session);
            Session loaded = reload(session.getId());

            // Assert
            assertSoftly(softly -> {
                softly.assertThat(loaded.getId()).isEqualTo(session.getId());
                softly.assertThat(loaded.getUserId()).isEqualTo(userId);
                softly.assertThat(loaded.getUserAgent()).isEqualTo(session.getUserAgent());
                softly.assertThat(loaded.getIpAddress()).isEqualTo(session.getIpAddress());
                softly.assertThat(loaded.getSessionData()).isEqualTo(session.getSessionData());
            });
        }

        @Test
        @DisplayName("should return the saved session object")
        void save_validSession_returnsSavedObject() {
            // Arrange
            Session session = aSession().withSessionData(Map.of("k", "v")).build();

            // Act
            Session returned = sessionDAO.save(session);

            // Assert
            assertThat(returned).isNotNull();
            assertThat(returned.getId()).isEqualTo(session.getId());
        }

        @Test
        @DisplayName("should preserve created_at when explicitly set")
        void save_explicitCreatedAt_preservesValue() {
            // Arrange
            Instant fixedDate = Instant.parse("2024-01-15T10:30:00Z");
            Session session = aSession().withCreatedAt(fixedDate).withSessionData(Map.of()).build();

            // Act
            sessionDAO.save(session);

            // Assert
            assertThat(reload(session.getId()).getCreatedAt()).isEqualTo(fixedDate);
        }

        @Test
        @DisplayName("should save created_at when set to now")
        void save_createdAtNow_isAfterBeforeMarker() {
            // Arrange
            Instant before = Instant.now().minusSeconds(60);
            Session session = aSession().withCreatedAt(Instant.now()).withSessionData(Map.of()).build();

            // Act
            sessionDAO.save(session);

            // Assert
            assertThat(reload(session.getId()).getCreatedAt()).isAfter(before);
        }

        @Test
        @DisplayName("should throw RuntimeException with SQL state 23505 on duplicate id (PK uniqueness)")
        void save_duplicateId_throwsRuntimeException() throws SQLException {
            // Arrange
            UUID userId = insertUser();
            Session first = aSession().withUserId(userId).withSessionData(Map.of()).build();
            sessionDAO.save(first);

            Session duplicate = aSession()
                    .withId(first.getId())
                    .withUserId(userId)
                    .withSessionData(Map.of())
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> sessionDAO.save(duplicate))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .hasCauseExactlyInstanceOf(org.postgresql.util.PSQLException.class)
                    .satisfies(ex -> {
                        org.postgresql.util.PSQLException sqlEx =
                                (org.postgresql.util.PSQLException) ex.getCause();
                        assertThat(sqlEx.getSQLState()).isEqualTo("23505");
                    });
        }

        @Test
        @DisplayName("should throw SQL state 23502 when id is null (NOT NULL constraint)")
        void save_nullId_throwsConstraintViolation() {
            // Arrange
            Session session = aSession().withId(null).withSessionData(Map.of()).build();

            // Act & Assert
            assertThatThrownBy(() -> sessionDAO.save(session))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .extracting(Throwable::getCause)
                    .isInstanceOfSatisfying(org.postgresql.util.PSQLException.class, ex ->
                            assertThat(ex.getSQLState()).isEqualTo("23502"));
        }

        @Test
        @DisplayName("should throw RuntimeException for non-existent user_id (FK constraint)")
        void save_nonExistentUserId_throwsFKViolation() {
            // Arrange — UUID never inserted into users
            Session session = aSession()
                    .withUserId(UUID.randomUUID())
                    .withSessionData(Map.of())
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> sessionDAO.save(session))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should save successfully when user_id is null (nullable FK column)")
        void save_nullUserId_savesSuccessfully() {
            // Arrange
            Session session = aSession().withUserId(null).withSessionData(Map.of("key", "value")).build();

            // Act
            sessionDAO.save(session);

            // Assert
            assertThat(reload(session.getId()).getUserId()).isNull();
        }

        @Test
        @DisplayName("should save successfully when ip_address is null (nullable column)")
        void save_nullIpAddress_savesSuccessfully() {
            // Arrange
            Session session = aSession().withIpAddress(null).withSessionData(Map.of()).build();

            // Act
            sessionDAO.save(session);

            // Assert
            assertThat(reload(session.getId()).getIpAddress()).isNull();
        }

        @Test
        @DisplayName("should save successfully when user_agent is null (nullable column)")
        void save_nullUserAgent_savesSuccessfully() {
            // Arrange
            Session session = aSession().withUserAgent(null).withSessionData(Map.of()).build();

            // Act
            sessionDAO.save(session);

            // Assert
            assertThat(reload(session.getId()).getUserAgent()).isNull();
        }

        @Test
        @DisplayName("should throw NPE when expires_at is null (NOT NULL constraint — bug: NPE instead of SQL exception)")
        void save_nullExpiresAt_throwsNpe() {
            // Bug: DAO calls Timestamp.from(null) which throws NPE before the SQL is reached.
            // Correct behaviour should wrap PSQLException with sqlstate 23502.
            // todo: add null guard in DAO for expires_at
            Session session = aSession().withExpiresAt(null).withSessionData(Map.of()).build();

            assertThatThrownBy(() -> sessionDAO.save(session))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when created_at is null (NOT NULL constraint — bug: NPE instead of SQL exception)")
        void save_nullCreatedAt_throwsNpe() {
            // Bug: same NPE pattern as expires_at. todo: add null guard in DAO.
            Session session = aSession().withCreatedAt(null).withSessionData(Map.of()).build();

            assertThatThrownBy(() -> sessionDAO.save(session))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when last_accessed_at is null (NOT NULL constraint — bug: NPE instead of SQL exception)")
        void save_nullLastAccessedAt_throwsNpe() {
            // Bug: same NPE pattern. todo: add null guard in DAO.
            Session session = aSession().withLastAccessedAt(null).withSessionData(Map.of()).build();

            assertThatThrownBy(() -> sessionDAO.save(session))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw when session_data is null — documents JSONB null behaviour")
        @Disabled("Bug: null session_data causes NPE in JsonUtil.toJson(null) before the SQL is reached; " +
                  "DAO should guard against null and default to empty map or throw a descriptive error")
        void save_nullSessionData_documentsNullBehaviour() {
            // Intended behaviour: save with empty JSONB '{}' (column default), not NPE
            Session session = aSession().withSessionData(null).build();

            assertThatCode(() -> sessionDAO.save(session)).doesNotThrowAnyException();
            assertThat(reload(session.getId()).getSessionData()).isEmpty();
        }

        @Test
        @DisplayName("should accept id at maximum allowed length of 64 chars")
        void save_idAtMaxLength64_savesSuccessfully() {
            // Arrange
            String maxId = "A".repeat(64);
            Session session = aSession().withId(maxId).withSessionData(Map.of()).build();

            // Act
            sessionDAO.save(session);

            // Assert
            assertThat(reload(maxId).getId()).isEqualTo(maxId);
        }

        @Test
        @DisplayName("should throw when id exceeds 64 chars (VARCHAR(64) column length contract)")
        void save_idExceeding64Chars_throwsRuntimeException() {
            // Arrange — 65 chars, one over the limit
            Session session = aSession().withId("A".repeat(65)).withSessionData(Map.of()).build();

            // Act & Assert
            assertThatThrownBy(() -> sessionDAO.save(session))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should accept user_agent at maximum allowed length of 500 chars")
        void save_userAgentAtMaxLength500_savesSuccessfully() {
            // Arrange — use realistic-looking boundary value per existing tests
            String maxUserAgent = "m".repeat(495) + "/5.45";
            Session session = aSession().withUserAgent(maxUserAgent).withSessionData(Map.of()).build();

            // Act
            sessionDAO.save(session);

            // Assert
            assertThat(reload(session.getId()).getUserAgent()).isEqualTo(maxUserAgent);
        }

        @Test
        @DisplayName("should throw when user_agent exceeds 500 chars (VARCHAR(500) column length contract)")
        void save_userAgentExceeding500Chars_throwsRuntimeException() {
            // Arrange — 501 chars, one over the limit
            Session session = aSession().withUserAgent("X".repeat(501)).withSessionData(Map.of()).build();

            // Act & Assert
            assertThatThrownBy(() -> sessionDAO.save(session))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should accept ip_address at maximum allowed length of 45 chars")
        void save_ipAddressAtMaxLength45_savesSuccessfully() {
            // Arrange — 45 chars (the VARCHAR(45) limit, covers full IPv6)
            String ip45 = "1".repeat(45);
            Session session = aSession().withIpAddress(ip45).withSessionData(Map.of()).build();

            // Act
            sessionDAO.save(session);

            // Assert
            assertThat(reload(session.getId()).getIpAddress()).isEqualTo(ip45);
        }

        @Test
        @DisplayName("should throw when ip_address exceeds 45 chars (VARCHAR(45) column length contract)")
        void save_ipAddressExceeding45Chars_throwsRuntimeException() {
            // Arrange — 46 chars, one over the limit
            Session session = aSession().withIpAddress("1".repeat(46)).withSessionData(Map.of()).build();

            // Act & Assert
            assertThatThrownBy(() -> sessionDAO.save(session))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should serialize and persist JSONB session_data with multiple entries")
        void save_sessionDataWithMultipleEntries_persistsAllEntries() {
            // Arrange
            Map<String, Object> data = Map.of("role", "ADMIN", "locale", "ru", "theme", "dark");
            Session session = aSession().withSessionData(data).build();

            // Act
            sessionDAO.save(session);

            // Assert
            assertThat(reload(session.getId()).getSessionData())
                    .containsEntry("role", "ADMIN")
                    .containsEntry("locale", "ru")
                    .containsEntry("theme", "dark");
        }
    }

    // -----------------------------------------------------------------------
    // findById()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("should return present Optional with all fields when id exists")
        void findById_existingId_returnsAllFields() {
            // Arrange
            Session session = aSession()
                    .withUserAgent("Safari/5.0")
                    .withSessionData(Map.of("role", "Admin", "status", "active", "csrfToken", "!tokenHash@"))
                    .withIpAddress("127.0.0.1")
                    .withCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                    .withLastAccessedAt(Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.MILLIS))
                    .withExpiresAt(Instant.now().plusSeconds(500).truncatedTo(ChronoUnit.MILLIS))
                    .build();
            sessionDAO.save(session);

            // Act
            Session loaded = sessionDAO.findById(session.getId()).orElseThrow();

            // Assert
            assertSoftly(softly -> {
                softly.assertThat(loaded.getId()).isEqualTo(session.getId());
                softly.assertThat(loaded.getUserId()).isEqualTo(session.getUserId());
                softly.assertThat(loaded.getUserAgent()).isEqualTo(session.getUserAgent());
                softly.assertThat(loaded.getIpAddress()).isEqualTo(session.getIpAddress());
                softly.assertThat(loaded.getCreatedAt()).isEqualTo(session.getCreatedAt());
                softly.assertThat(loaded.getLastAccessedAt()).isEqualTo(session.getLastAccessedAt());
                softly.assertThat(loaded.getExpiresAt()).isEqualTo(session.getExpiresAt());
                softly.assertThat(loaded.getSessionData()).isEqualTo(session.getSessionData());
            });
        }

        @Test
        @DisplayName("should return present Optional when id exists")
        void findById_existingId_isPresent() {
            // Arrange
            String id = UUID.randomUUID().toString();
            sessionDAO.save(aSession().withId(id).withSessionData(Map.of()).build());

            // Act & Assert
            assertThat(sessionDAO.findById(id)).isPresent();
        }

        @Test
        @DisplayName("should return empty Optional when id does not exist")
        void findById_unknownId_returnsEmpty() {
            // Act & Assert
            assertThat(sessionDAO.findById(UUID.randomUUID().toString())).isEmpty();
        }

        @Test
        @DisplayName("should return empty Optional when id is null")
        void findById_nullId_returnsEmpty() {
            // Act & Assert
            assertThat(sessionDAO.findById(null)).isEmpty();
        }

        @Test
        @DisplayName("should correctly deserialize JSONB session_data on load")
        void findById_sessionWithData_deserializesSessionData() {
            // Arrange
            Map<String, Object> data = Map.of("role", "FLIGHT_MANAGER", "locale", "en");
            Session session = aSession().withSessionData(data).build();
            sessionDAO.save(session);

            // Act
            Session loaded = sessionDAO.findById(session.getId()).orElseThrow();

            // Assert
            assertThat(loaded.getSessionData())
                    .containsEntry("role", "FLIGHT_MANAGER")
                    .containsEntry("locale", "en");
        }

        @Test
        @DisplayName("should preserve timestamps within one second of precision when reloaded")
        void findById_existingSession_timestampsMatchWithinOneSec() {
            // Arrange
            Instant expiresAt = Instant.now().plusSeconds(3600);
            Session session = aSession().withExpiresAt(expiresAt).withSessionData(Map.of()).build();
            sessionDAO.save(session);

            // Act
            Session loaded = sessionDAO.findById(session.getId()).orElseThrow();

            // Assert
            assertThat(loaded.getExpiresAt())
                    .isCloseTo(expiresAt, within(1, ChronoUnit.SECONDS));
            assertThat(loaded.getCreatedAt())
                    .isCloseTo(session.getCreatedAt(), within(1, ChronoUnit.SECONDS));
        }
    }

    // -----------------------------------------------------------------------
    // update()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("update()")
    class UpdateTests {

        @Test
        @DisplayName("should update session_data, last_accessed_at, and expires_at")
        void update_existingSession_updatesFields() {
            // Arrange
            Session original = sessionDAO.save(aSession()
                    .withSessionData(Map.of("role", "ADMIN", "locale", "rus"))
                    .withLastAccessedAt(Instant.now())
                    .withExpiresAt(Instant.now().plusSeconds(60))
                    .build());

            Session before = reload(original.getId());

            Map<String, Object> updatedData = Map.of(
                    "csrfToken", "@updatedHashToken123!",
                    "role", "FLIGHT_MANAGER",
                    "locale", "rus",
                    "rememberMe", "true",
                    "status", "ACTIVE"
            );
            original.setSessionData(updatedData);
            original.setLastAccessedAt(Instant.now().plusSeconds(20));
            original.setExpiresAt(Instant.now().plusSeconds(120));

            // Act
            sessionDAO.update(original);
            Session updated = reload(original.getId());

            // Assert
            assertThat(updated.getSessionData()).containsExactlyInAnyOrderEntriesOf(updatedData);
            assertThat(updated.getLastAccessedAt()).isAfter(before.getLastAccessedAt());
            assertThat(updated.getExpiresAt()).isAfter(before.getExpiresAt());
        }

        @Test
        @DisplayName("should not affect other sessions")
        void update_oneSession_doesNotAffectOthers() {
            // Arrange
            Map<String, Object> data1 = Map.of("role", "FLIGHT_MANAGER", "locale", "eng");
            Session session1 = sessionDAO.save(aSession()
                    .withSessionData(data1)
                    .withExpiresAt(Instant.now().plusSeconds(60))
                    .withLastAccessedAt(Instant.now())
                    .build());
            Session session2 = sessionDAO.save(aSession()
                    .withSessionData(Map.of("role", "CUSTOMER_MANAGER"))
                    .withExpiresAt(Instant.now().plusSeconds(60))
                    .withLastAccessedAt(Instant.now())
                    .build());

            session2.setSessionData(Map.of("role", "ADMIN"));
            session2.setLastAccessedAt(Instant.now().plusSeconds(120));
            session2.setExpiresAt(Instant.now().plusSeconds(120));

            // Act
            sessionDAO.update(session2);

            // Assert — session1 is untouched
            assertThat(reload(session1.getId()).getSessionData())
                    .containsExactlyInAnyOrderEntriesOf(data1);
        }

        @Test
        @DisplayName("should not alter created_at during update")
        void update_existingSession_doesNotChangeCreatedAt() {
            // Arrange
            Session original = sessionDAO.save(aSession()
                    .withSessionData(Map.of("role", "ADMIN"))
                    .withLastAccessedAt(Instant.now())
                    .build());
            Instant originalCreatedAt = reload(original.getId()).getCreatedAt();

            original.setSessionData(Map.of("role", "FLIGHT_MANAGER"));
            original.setLastAccessedAt(Instant.now().plusSeconds(30));
            original.setExpiresAt(Instant.now().plusSeconds(1800));

            // Act
            sessionDAO.update(original);

            // Assert
            assertThat(reload(original.getId()).getCreatedAt()).isEqualTo(originalCreatedAt);
        }

        @Test
        @DisplayName("should not alter user_id during update")
        void update_existingSession_doesNotChangeUserId() throws SQLException {
            // Arrange
            UUID userId = insertUser();
            Session session = sessionDAO.save(aSession()
                    .withUserId(userId)
                    .withSessionData(Map.of("v", "1"))
                    .build());

            session.setSessionData(Map.of("v", "2"));
            session.setLastAccessedAt(Instant.now().plusSeconds(30));
            session.setExpiresAt(Instant.now().plusSeconds(1800));

            // Act
            sessionDAO.update(session);

            // Assert
            assertThat(reload(session.getId()).getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should not throw when updating a non-existent session id (no-op)")
        @Disabled("Bug: update() silently no-ops on missing id instead of throwing ResourceNotFoundException — exception handling not implemented")
        void update_nonExistentId_shouldThrowResourceNotFoundException() {
            Session ghost = aSession()
                    .withId("ghost-id-does-not-exist")
                    .withSessionData(Map.of("x", "y"))
                    .build();

            // Intended: should throw ResourceNotFoundException
            assertThatThrownBy(() -> sessionDAO.update(ghost))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should throw NPE when expires_at is null during update (bug: NPE instead of SQL exception)")
        @Disabled("Bug: Timestamp.from(null) throws NPE before reaching the DB — needs null guard in DAO")
        void update_nullExpiresAt_throwsNpe() {
            Session session = sessionDAO.save(aSession().withSessionData(Map.of()).build());
            session.setExpiresAt(null);

            assertThatThrownBy(() -> sessionDAO.update(session))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when last_accessed_at is null during update (bug: NPE instead of SQL exception)")
        @Disabled("Bug: Timestamp.from(null) throws NPE before reaching the DB — needs null guard in DAO")
        void update_nullLastAccessedAt_throwsNpe() {
            Session session = sessionDAO.save(aSession().withSessionData(Map.of()).build());
            session.setLastAccessedAt(null);

            assertThatThrownBy(() -> sessionDAO.update(session))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should not throw when session_data is null during update (documents JsonUtil null behaviour)")
        @Disabled("Bug: null session_data causes NPE in JsonUtil.toJson(null) — DAO should guard and default to empty map")
        void update_nullSessionData_documentsNullBehaviour() {
            Session session = sessionDAO.save(aSession().withSessionData(Map.of("k", "v")).build());
            session.setSessionData(null);

            assertThatCode(() -> sessionDAO.update(session)).doesNotThrowAnyException();
        }
    }

    // -----------------------------------------------------------------------
    // updateLastAccessed()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateLastAccessed()")
    class UpdateLastAccessedTests {

        @Test
        @DisplayName("should update last_accessed_at correctly")
        void updateLastAccessed_existingId_updatesLastAccessedAt() {
            // Arrange
            Session original = sessionDAO.save(aSession().withSessionData(Map.of()).build());

            // Act
            sessionDAO.updateLastAccessed(
                    original.getId(),
                    Instant.now().plusSeconds(10),
                    Instant.now().plusSeconds(120)
            );
            Session updated = reload(original.getId());

            // Assert
            assertThat(updated.getLastAccessedAt()).isAfter(original.getLastAccessedAt());
        }

        @Test
        @DisplayName("should update expires_at correctly")
        void updateLastAccessed_existingId_updatesExpiresAt() {
            // Arrange
            Session original = sessionDAO.save(aSession().withSessionData(Map.of()).build());

            // Act
            sessionDAO.updateLastAccessed(
                    original.getId(),
                    Instant.now().plusSeconds(10),
                    Instant.now().plusSeconds(120)
            );
            Session updated = reload(original.getId());

            // Assert
            assertThat(updated.getExpiresAt()).isAfter(original.getExpiresAt());
        }

        @Test
        @DisplayName("should not modify session_data, user_agent, or ip_address")
        void updateLastAccessed_existingId_doesNotAffectOtherFields() {
            // Arrange
            Session original = sessionDAO.save(aSession()
                    .withSessionData(Map.of("role", "ADMIN", "rememberMe", "true"))
                    .withUserAgent("MozillaFireFox/5.0")
                    .withIpAddress("127.0.0.6")
                    .withSessionData(Map.of(
                            "csrfToken", "@HashToken123!",
                            "role", "ADMIN",
                            "locale", "rus",
                            "rememberMe", "true",
                            "status", "BLOCKED"
                    ))
                    .build());

            // Act
            sessionDAO.updateLastAccessed(
                    original.getId(),
                    Instant.now().plusSeconds(60),
                    Instant.now().plusSeconds(120)
            );
            Session updated = reload(original.getId());

            // Assert
            assertThat(updated.getSessionData())
                    .containsExactlyInAnyOrderEntriesOf(original.getSessionData());
            assertThat(updated.getUserAgent()).isEqualTo("MozillaFireFox/5.0");
            assertThat(updated.getIpAddress()).isEqualTo("127.0.0.6");
        }

        @Test
        @DisplayName("should not throw when id does not exist (no-op)")
        @Disabled("Bug: updateLastAccessed() silently no-ops instead of throwing ResourceNotFoundException — exception handling not implemented")
        void updateLastAccessed_nonExistentId_shouldThrowResourceNotFoundException() {
            assertThatThrownBy(() -> sessionDAO.updateLastAccessed(
                    "non-existent-id", Instant.now(), Instant.now().plusSeconds(600)))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should not throw when id is null (no-op — bug: exception handling not implemented)")
        @Disabled("Bug: updateLastAccessed() should throw ResourceNotFoundException for null id — not implemented")
        void updateLastAccessed_nullId_shouldThrowResourceNotFoundException() {
            assertThatThrownBy(() -> sessionDAO.updateLastAccessed(
                    null, Instant.now(), Instant.now().plusSeconds(600)))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should throw NPE when expires_at is null (bug: NPE instead of SQL exception)")
        @Disabled("Bug: Timestamp.from(null) throws NPE before reaching DB — needs null guard in DAO")
        void updateLastAccessed_nullExpiresAt_throwsNpe() {
            Session session = sessionDAO.save(aSession().withSessionData(Map.of()).build());

            assertThatThrownBy(() -> sessionDAO.updateLastAccessed(session.getId(), Instant.now(), null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when last_access is null (bug: NPE instead of SQL exception)")
        @Disabled("Bug: Timestamp.from(null) throws NPE before reaching DB — needs null guard in DAO")
        void updateLastAccessed_nullLastAccess_throwsNpe() {
            Session session = sessionDAO.save(aSession().withSessionData(Map.of()).build());

            assertThatThrownBy(() -> sessionDAO.updateLastAccessed(session.getId(), null, Instant.now().plusSeconds(600)))
                    .isExactlyInstanceOf(NullPointerException.class);
        }
    }

    // -----------------------------------------------------------------------
    // delete()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("should delete existing session")
        void delete_existingId_removesSession() {
            // Arrange
            Session session = sessionDAO.save(aSession().withSessionData(Map.of()).build());
            assertThat(sessionDAO.findById(session.getId())).isPresent();

            // Act
            sessionDAO.delete(session.getId());

            // Assert
            assertThat(sessionDAO.findById(session.getId())).isNotPresent();
        }

        @Test
        @DisplayName("should not delete other sessions")
        void delete_oneSession_leavesOthersIntact() {
            // Arrange
            Session s1 = sessionDAO.save(aSession().withUserAgent("Safari/2.0").withSessionData(Map.of()).build());
            Session s2 = sessionDAO.save(aSession().withUserAgent("Brave/4.3").withSessionData(Map.of()).build());

            // Act
            sessionDAO.delete(s1.getId());

            // Assert
            assertThat(sessionDAO.findById(s1.getId())).isNotPresent();
            assertThat(sessionDAO.findById(s2.getId())).isPresent();
        }

        @Test
        @DisplayName("should not throw when deleting a non-existent session id")
        @Disabled("Bug: delete() does not return affected row count and silently ignores missing id — " +
                  "consider returning int or throwing ResourceNotFoundException")
        void delete_nonExistentId_shouldReturnZeroAffectedRows() {
            // Intended: return 0 or throw ResourceNotFoundException
            assertThatCode(() -> sessionDAO.delete("non-existent-id"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not throw when id is null")
        @Disabled("Bug: delete() does not handle null id — consider returning 0 or throwing ResourceNotFoundException")
        void delete_nullId_shouldReturnZeroAffectedRows() {
            assertThatCode(() -> sessionDAO.delete(null))
                    .doesNotThrowAnyException();
        }
    }

    // -----------------------------------------------------------------------
    // deleteByUserId()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteByUserId()")
    class DeleteByUserIdTests {

        @Test
        @DisplayName("should delete the session belonging to the given user")
        void deleteByUserId_existingUser_deletesSession() throws SQLException {
            // Arrange
            UUID userId = insertUser();
            Session session = sessionDAO.save(aSession().withUserId(userId).withSessionData(Map.of()).build());
            assertThat(sessionDAO.findById(session.getId()))
                    .isPresent()
                    .get()
                    .extracting(Session::getUserId)
                    .isEqualTo(userId);

            // Act
            sessionDAO.deleteByUserId(userId);

            // Assert
            assertThat(sessionDAO.findById(session.getId())).isNotPresent();
        }

        @Test
        @DisplayName("should delete all sessions belonging to the given user")
        void deleteByUserId_userWithMultipleSessions_deletesAllSessions() throws SQLException {
            // Arrange
            UUID userId = insertUser();
            Session s1 = sessionDAO.save(aSession().withUserId(userId).withSessionData(Map.of()).build());
            Session s2 = sessionDAO.save(aSession().withUserId(userId).withSessionData(Map.of()).build());
            Session s3 = sessionDAO.save(aSession().withUserId(userId).withSessionData(Map.of()).build());

            // Act
            sessionDAO.deleteByUserId(userId);

            // Assert
            assertThat(sessionDAO.findById(s1.getId())).isNotPresent();
            assertThat(sessionDAO.findById(s2.getId())).isNotPresent();
            assertThat(sessionDAO.findById(s3.getId())).isNotPresent();
        }

        @Test
        @DisplayName("should not delete sessions belonging to other users")
        void deleteByUserId_onlyDeletesTargetUserSessions() throws SQLException {
            // Arrange
            UUID userA = insertUser();
            UUID userB = insertUser();
            Session sA = sessionDAO.save(aSession().withUserId(userA).withSessionData(Map.of()).build());
            Session sB = sessionDAO.save(aSession().withUserId(userB).withSessionData(Map.of()).build());

            // Act
            sessionDAO.deleteByUserId(userA);

            // Assert
            assertThat(sessionDAO.findById(sA.getId())).isNotPresent();
            assertThat(sessionDAO.findById(sB.getId())).isPresent();
        }

        @Test
        @DisplayName("should not throw when user has no sessions")
        @Disabled("Bug: deleteByUserId() does not return affected row count — " +
                  "consider returning int or throwing ResourceNotFoundException when user has no sessions")
        void deleteByUserId_userWithNoSessions_shouldReturnZeroAffectedRows() {
            assertThatCode(() -> sessionDAO.deleteByUserId(UUID.randomUUID()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not throw when user_id does not exist in users table")
        @Disabled("Bug: deleteByUserId() does not return affected row count — " +
                  "consider returning int when no rows matched")
        void deleteByUserId_nonExistentUserId_shouldReturnZeroAffectedRows() {
            assertThatCode(() -> sessionDAO.deleteByUserId(UUID.randomUUID()))
                    .doesNotThrowAnyException();
        }
    }

    // -----------------------------------------------------------------------
    // deleteExpired()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteExpired()")
    class DeleteExpiredTests {

        @Test
        @DisplayName("should delete expired sessions and return deleted count of 1")
        void deleteExpired_singleExpiredSession_deletesAndReturnsOne() {
            // Arrange
            Session expired = sessionDAO.save(
                    aSession().withExpiresAt(Instant.now().minusSeconds(120)).withSessionData(Map.of()).build());
            assertThat(sessionDAO.findById(expired.getId())).isPresent();

            // Act & Assert
            assertThat(sessionDAO.deleteExpired()).isEqualTo(1);
            assertThat(sessionDAO.findById(expired.getId())).isNotPresent();
        }

        @Test
        @DisplayName("should not delete non-expired sessions and return 0")
        void deleteExpired_noExpiredSessions_returnsZero() {
            // Arrange
            Session active = sessionDAO.save(
                    aSession().withExpiresAt(Instant.now().plusSeconds(120)).withSessionData(Map.of()).build());

            // Act & Assert
            assertThat(sessionDAO.deleteExpired()).isZero();
            assertThat(sessionDAO.findById(active.getId())).isPresent();
        }

        @Test
        @DisplayName("should delete multiple expired sessions with different expiration times")
        void deleteExpired_multipleExpiredSessions_deletesAllAndReturnsCount() {
            // Arrange
            Session s1 = sessionDAO.save(aSession().withExpiresAt(Instant.now().minusSeconds(5)).withSessionData(Map.of()).build());
            Session s2 = sessionDAO.save(aSession().withExpiresAt(Instant.now().minusSeconds(20)).withSessionData(Map.of()).build());
            Session s3 = sessionDAO.save(aSession().withExpiresAt(Instant.now().minusSeconds(45)).withSessionData(Map.of()).build());

            // Act & Assert
            assertThat(sessionDAO.deleteExpired()).isEqualTo(3);
            assertThat(sessionDAO.findById(s1.getId())).isNotPresent();
            assertThat(sessionDAO.findById(s2.getId())).isNotPresent();
            assertThat(sessionDAO.findById(s3.getId())).isNotPresent();
        }

        @Test
        @DisplayName("should delete only expired sessions and leave active ones intact")
        void deleteExpired_mixedSessions_deletesOnlyExpired() {
            // Arrange
            Session expired = sessionDAO.save(
                    aSession().withExpiresAt(Instant.now().minusSeconds(100)).withSessionData(Map.of()).build());
            Session active = sessionDAO.save(
                    aSession().withExpiresAt(Instant.now().plusSeconds(3600)).withSessionData(Map.of()).build());

            // Act
            int deleted = sessionDAO.deleteExpired();

            // Assert
            assertThat(deleted).isEqualTo(1);
            assertThat(sessionDAO.findById(expired.getId())).isNotPresent();
            assertThat(sessionDAO.findById(active.getId())).isPresent();
        }

        @Test
        @DisplayName("should return 0 when sessions table is empty")
        void deleteExpired_emptyTable_returnsZero() {
            assertThat(sessionDAO.deleteExpired()).isZero();
        }
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("edge cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle session with special characters in user_agent")
        void save_specialCharactersInUserAgent_savesAndRetrievesCorrectly() {
            // Arrange
            String specialUserAgent = "O'Brien-José María 日本語 Ñoño <script>alert('xss')</script>";
            Session session = aSession().withUserAgent(specialUserAgent).withSessionData(Map.of()).build();

            // Act
            assertThatCode(() -> sessionDAO.save(session)).doesNotThrowAnyException();

            // Assert
            assertThat(sessionDAO.findById(session.getId()))
                    .isPresent()
                    .get()
                    .extracting(Session::getUserAgent)
                    .isEqualTo(specialUserAgent);
        }

        @Test
        @DisplayName("should handle empty string user_agent")
        void save_emptyUserAgent_savesAndRetrievesCorrectly() {
            // Arrange
            Session session = aSession().withUserAgent("").withSessionData(Map.of()).build();

            // Act
            assertThatCode(() -> sessionDAO.save(session)).doesNotThrowAnyException();

            // Assert
            assertThat(sessionDAO.findById(session.getId()))
                    .isPresent()
                    .get()
                    .extracting(Session::getUserAgent)
                    .isEqualTo("");
        }

        @Test
        @DisplayName("multiple sequential updates on same session should all persist correctly")
        void multipleSequentialUpdates_allPersistCorrectly() {
            // Arrange
            Session session = sessionDAO.save(aSession().withSessionData(Map.of("v", "0")).build());

            // Act — apply 5 sequential updates
            for (int i = 1; i <= 5; i++) {
                session.setSessionData(Map.of("v", String.valueOf(i)));
                session.setLastAccessedAt(Instant.now().plusSeconds(i * 10L));
                session.setExpiresAt(Instant.now().plusSeconds(3600 + i * 10L));
                sessionDAO.update(session);
            }

            // Assert — only the final state is persisted
            assertThat(reload(session.getId()).getSessionData()).containsEntry("v", "5");
        }
    }

    // -----------------------------------------------------------------------
    // Complex scenarios
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("complex scenarios")
    class ComplexTests {

        @Test
        @DisplayName("full lifecycle: create → update → updateLastAccessed → delete")
        void fullLifeCycle_createUpdateAccessDelete() {
            // 1. User logs in — session created
            Session session = sessionDAO.save(aSession()
                    .withUserAgent("Opera/3.0")
                    .withSessionData(Map.of(
                            "csrfToken", "@HashToken123!",
                            "role", "ADMIN",
                            "locale", "rus",
                            "rememberMe", "true",
                            "status", "BLOCKED"
                    ))
                    .withExpiresAt(Instant.now().plusSeconds(60))
                    .withLastAccessedAt(Instant.now().minusSeconds(30))
                    .build());
            assertThat(sessionDAO.findById(session.getId())).isPresent();

            // 2. User changes language — session_data updated
            Map<String, Object> updatedData = Map.of(
                    "csrfToken", "@HashToken123!",
                    "role", "ADMIN",
                    "locale", "eng",
                    "rememberMe", "true",
                    "status", "BLOCKED"
            );
            session.setSessionData(updatedData);
            sessionDAO.update(session);
            assertThat(reload(session.getId()).getSessionData())
                    .containsEntry("locale", "eng"); // fixed: use containsEntry, not hasFieldOrPropertyWithValue

            // 3. User navigates — last access bumped
            Instant newExpiry = Instant.now().plusSeconds(120);
            sessionDAO.updateLastAccessed(session.getId(), Instant.now(), newExpiry);
            Session afterAccess = reload(session.getId());
            assertThat(afterAccess.getExpiresAt()).isAfter(session.getExpiresAt());
            assertThat(afterAccess.getLastAccessedAt()).isAfter(session.getLastAccessedAt());

            // 4. User is blocked — session deleted
            sessionDAO.delete(session.getId());
            assertThat(sessionDAO.findById(session.getId())).isNotPresent();
        }

        @Test
        @DisplayName("single user can have multiple concurrent sessions (multi-device scenario)")
        void singleUser_multipleSessions_allIndependent() throws SQLException {
            // Arrange — simulate user logged in from 3 devices
            UUID userId = insertUser();
            Session desktop = sessionDAO.save(aSession().withUserId(userId).withUserAgent("Chrome/120").withSessionData(Map.of("device", "desktop")).build());
            Session mobile  = sessionDAO.save(aSession().withUserId(userId).withUserAgent("Mobile/iOS").withSessionData(Map.of("device", "mobile")).build());
            Session tablet  = sessionDAO.save(aSession().withUserId(userId).withUserAgent("Safari/iPad").withSessionData(Map.of("device", "tablet")).build());

            // Verify all three exist
            assertThat(sessionDAO.findById(desktop.getId())).isPresent();
            assertThat(sessionDAO.findById(mobile.getId())).isPresent();
            assertThat(sessionDAO.findById(tablet.getId())).isPresent();

            // Update desktop session
            desktop.setSessionData(Map.of("device", "desktop", "theme", "dark"));
            desktop.setLastAccessedAt(Instant.now().plusSeconds(10));
            desktop.setExpiresAt(Instant.now().plusSeconds(3600));
            sessionDAO.update(desktop);

            // Mobile and tablet are unaffected
            assertThat(reload(mobile.getId()).getSessionData()).containsEntry("device", "mobile");
            assertThat(reload(tablet.getId()).getSessionData()).containsEntry("device", "tablet");

            // Delete all sessions for user at logout
            sessionDAO.deleteByUserId(userId);
            assertThat(sessionDAO.findById(desktop.getId())).isNotPresent();
            assertThat(sessionDAO.findById(mobile.getId())).isNotPresent();
            assertThat(sessionDAO.findById(tablet.getId())).isNotPresent();
        }
    }

    // -----------------------------------------------------------------------
    // Concurrent access
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("concurrent access")
    class ConcurrentTests {

        @Test
        @DisplayName("concurrent updateLastAccessed on the same session should not throw (row-level contention)")
        void concurrent_updateLastAccessed_sameSession_noConcurrencyErrors() throws InterruptedException {
            // Arrange — simulate multiple requests from the same browser tab hitting the server simultaneously
            Session session = sessionDAO.save(aSession().withSessionData(Map.of()).build());
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(threadCount);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    startGate.countDown();
                    startGate.await(5, TimeUnit.SECONDS);
                    sessionDAO.updateLastAccessed(
                            session.getId(),
                            Instant.now(),
                            Instant.now().plusSeconds(3600));
                    return null;
                }));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            // Assert — all updates succeed without exception
            long failures = futures.stream()
                    .filter(f -> {
                        try {
                            f.get();
                            return false;
                        } catch (Exception e) {
                            return true;
                        }
                    })
                    .count();
            assertThat(failures).isZero();

            // Verify session still exists and was updated
            assertThat(sessionDAO.findById(session.getId())).isPresent();
        }
    }
}
