package integrationtests.DAO.loginAttemptsDAO;

import com.airline.airlinebackend.dao.BaseDAO;
import com.airline.airlinebackend.dao.LoginAttemptDAO;
import com.airline.airlinebackend.model.LoginAttempt;
import integrationtests.DAO.AbstractDAOIT;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import static org.assertj.core.api.Assertions.*;

public class LoginAttemptsDAOIT extends AbstractDAOIT {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptsDAOIT.class);
    private LoginAttemptDAO loginAttemptDAO;

    @BeforeEach
    public void setup() {
        loginAttemptDAO = new LoginAttemptDAO();
    }

    private Optional<LoginAttempt>findByEmail(String email) {
        LoginAttempt loginAttempt = new LoginAttempt();
        var sql = """
                SELECT id, email, ip_address, attempted_at, success 
                FROM login_attempts WHERE email = ?
                """;

        try(Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,email);
            try(ResultSet rs = ps.executeQuery()) {
                if(rs.next()) {
                    loginAttempt.setId(rs.getLong("id"));
                    loginAttempt.setEmail(rs.getString("email"));
                    loginAttempt.setIpAddress(rs.getString("ip_address"));
                    loginAttempt.setAttemptedAt(rs.getTimestamp("attempted_at").toInstant());
                    loginAttempt.setSuccess(rs.getBoolean("success"));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException ex) {
            log.debug("FindByEmail() test throws error: {}", ex.getMessage());
            return Optional.empty();
        }
        return Optional.of(loginAttempt);
    }

    @Nested
    @DisplayName("save() tests")
    class saveTests {
        @Test
        @DisplayName("should persists with all corrected field")
        void saveAllFieldsAreCorrect() {
            LoginAttempt original = new LoginAttempt("test@com","192.0.0.1",true);
            loginAttemptDAO.save(original);

            LoginAttempt retrieved = findByEmail(original.getEmail()).orElseThrow();

            assertSoftly( softly -> {
                softly.assertThat(retrieved.getId()).isEqualTo(1);
                softly.assertThat(retrieved.getIpAddress()).isEqualTo(original.getIpAddress());
                softly.assertThat(retrieved.getAttemptedAt()).isCloseTo(original.getAttemptedAt(), within(1, ChronoUnit.SECONDS));
                softly.assertThat(retrieved.isSuccess()).isEqualTo(original.isSuccess());
            });


        }

        @Test
        @DisplayName("save with null email should throw notNullConstraintViolation")
        void saveNullEmail() {
            LoginAttempt original = new LoginAttempt(null,"192.0.0.2",false);

            assertThatCode(() -> loginAttemptDAO.save(original)).isInstanceOf(RuntimeException.class)
                    .satisfies(ex -> {
                        SQLException sqlException = (SQLException) ex.getCause();
                        assertThat(sqlException.getSQLState()).isEqualTo("23502");
                    });
        }

        @Test
        @DisplayName("save with null ip_address should throw notNullConstraintViolation")
        void saveNullIpAddress() {
            LoginAttempt original = new LoginAttempt("test@com",null,true);

            assertThatCode(() -> loginAttemptDAO.save(original)).isInstanceOf(RuntimeException.class)
                    .satisfies(ex -> {
                        SQLException sqlException = (SQLException) ex.getCause();
                        assertThat(sqlException.getSQLState()).isEqualTo("23502");
                    });
        }

        @Test
        @Disabled("BUG: save() throws NPE via Timestamp.from() before DB NOT NULL constraint")
        @DisplayName("save with null attempted_at should throw notNullConstraintViolation")
        void saveNullAttemptedAt() {}

        @Test
        @DisplayName("save  email max length with 255 chars should succeed")
        void saveEmailMaxLengthWith255Chars() {
            String longEmail = "q".repeat(255);
            LoginAttempt original = new LoginAttempt(longEmail,"192.0.0.2",true);

            assertThatCode(() -> loginAttemptDAO.save(original)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("save email over 255 length should throw constraint violation")
        void saveEmailOver255Length() {
            String longEmail = "q".repeat(256);
            LoginAttempt original = new LoginAttempt(longEmail,"192.0.0.2",true);

            assertThatCode(() -> loginAttemptDAO.save(original)).isInstanceOf(RuntimeException.class)
                    .satisfies(ex -> {
                        SQLException sqlException = (SQLException) ex.getCause();
                        assertThat(sqlException.getSQLState()).isEqualTo("22001");
                    });
        }

        @Test
        @DisplayName("save ip_address max length 45 should succeed")
        void saveIpAddressMaxLength45() {
            String longIpAddress = "1".repeat(45);
            LoginAttempt original = new LoginAttempt("test@mail",longIpAddress,true);

            assertThatCode(() -> loginAttemptDAO.save(original)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("save ip_address over 45 length should throw constraint violation")
        void saveIpAddress_Over45Length() {
            String longIpAddress = "1".repeat(46);
            LoginAttempt original = new LoginAttempt("test@mail",longIpAddress,true);

            assertThatCode(() -> loginAttemptDAO.save(original)).isInstanceOf(RuntimeException.class)
                    .satisfies(ex -> {
                        SQLException sqlException = (SQLException) ex.getCause();
                        assertThat(sqlException.getSQLState()).isEqualTo("22001");
                    });
        }
    }

    @Nested
    @DisplayName("countFailedAttemptsByEmail() tests")
    class countFailedAttemptsByEmailTests {

        @Test
        @DisplayName("should count all failed attempts by email")
        void countAllFailedAttemptsByEmail() {
            LoginAttempt failedAttempt1 = new LoginAttempt("test@com","192.0.0.2",false);
            LoginAttempt failedAttempt2 = new LoginAttempt("test@com","192.0.0.3",false);
            LoginAttempt failedAttempt3 = new LoginAttempt("test@com","192.0.0.4",false);

            loginAttemptDAO.save(failedAttempt1);
            loginAttemptDAO.save(failedAttempt2);
            loginAttemptDAO.save(failedAttempt3);

            assertThat(loginAttemptDAO.countFailedAttemptsByEmail("test@com",
                    Instant.now().minusSeconds(12))).isEqualTo(3);

        }

        @Test
        @DisplayName("should not count success attempts by email")
        void countOnlyFailedAttemptsByEmail() {
            LoginAttempt failedAttempt1 = new LoginAttempt("test@com","192.0.0.2",false);
            LoginAttempt failedAttempt2 = new LoginAttempt("test@com","192.0.0.3",false);
            LoginAttempt successAttempt1 = new LoginAttempt("test@com","192.0.0.3",true);

            loginAttemptDAO.save(failedAttempt1);
            loginAttemptDAO.save(failedAttempt2);
            loginAttemptDAO.save(successAttempt1);

            assertThat(loginAttemptDAO.countFailedAttemptsByEmail("test@com",
                    Instant.now().minusSeconds(12))).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("countFailedAttemptsByIpAddress() tests")
    class countFailedAttemptsByIpAddressTests {

        @Test
        @DisplayName("should count all failed attempts by ip_address")
        void countAllFailedAttemptsByIpAddress() {
            LoginAttempt failedAttempt1 = new LoginAttempt("test@com","192.0.0.2",false);
            LoginAttempt failedAttempt2 = new LoginAttempt("test@com","192.0.0.2",false);
            LoginAttempt failedAttempt3 = new LoginAttempt("test@com","192.0.0.2",false);

            loginAttemptDAO.save(failedAttempt1);
            loginAttemptDAO.save(failedAttempt2);
            loginAttemptDAO.save(failedAttempt3);

            assertThat(loginAttemptDAO.countFailedAttemptsByIpAddress("192.0.0.2",
                    Instant.now().minusSeconds(12))).isEqualTo(3);
        }

        @Test
        @DisplayName("should not count success attempts by id_address")
        void countOnlyFailedAttemptsByIdAddress() {
            LoginAttempt failedAttempt1 = new LoginAttempt("test@com","192.0.0.2",false);
            LoginAttempt failedAttempt2 = new LoginAttempt("test@com","192.0.0.2",false);
            LoginAttempt successAttempt1 = new LoginAttempt("test@com","192.0.0.2",true);

            loginAttemptDAO.save(failedAttempt1);
            loginAttemptDAO.save(failedAttempt2);
            loginAttemptDAO.save(successAttempt1);

            assertThat(loginAttemptDAO.countFailedAttemptsByIpAddress("192.0.0.2",
                    Instant.now().minusSeconds(12))).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("deleteOlderThen() tests")
    class deleteOlderThenTests {

        @Test
        @DisplayName("should delete all older then")
        void deleteOlderThen() {
            LoginAttempt oldestAttempt1 = new LoginAttempt("test@com","192.0.0.2",false);
            oldestAttempt1.setAttemptedAt(Instant.now().minusSeconds(12));

            LoginAttempt oldestAttempt2 = new LoginAttempt("test2@com","192.0.3.2",true);
            oldestAttempt2.setAttemptedAt(Instant.now().minusSeconds(5));

            LoginAttempt oldestAttempt3 = new LoginAttempt("test3@com","192.0.3.5",true);
            oldestAttempt3.setAttemptedAt(Instant.now().minusSeconds(50));

            loginAttemptDAO.save(oldestAttempt1);
            loginAttemptDAO.save(oldestAttempt2);
            loginAttemptDAO.save(oldestAttempt3);

            assertThat(loginAttemptDAO.deleteOlderThen(Instant.now())).isEqualTo(3);


        }

        @Test
        @DisplayName("should not delete attempts not older")
        void deleteOlderThenNotOlder() {
            LoginAttempt oldestAttempt1 = new LoginAttempt("test@com","192.0.0.2",false);
            oldestAttempt1.setAttemptedAt(Instant.now().minusSeconds(12));

            LoginAttempt oldestAttempt2 = new LoginAttempt("test2@com","192.0.3.2",true);
            oldestAttempt2.setAttemptedAt(Instant.now().minusSeconds(5));

            LoginAttempt newAttempt1 = new LoginAttempt("test3@com","192.0.3.5",true);
            newAttempt1.setAttemptedAt(Instant.now().plusSeconds(50));

            loginAttemptDAO.save(oldestAttempt1);
            loginAttemptDAO.save(oldestAttempt2);
            loginAttemptDAO.save(newAttempt1);

            assertThat(loginAttemptDAO.deleteOlderThen(Instant.now())).isEqualTo(2);
        }
    }

}
