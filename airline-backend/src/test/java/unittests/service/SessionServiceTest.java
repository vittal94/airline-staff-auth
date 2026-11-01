package unittests.service;

import com.airline.airlinebackend.dao.EmailConfirmationTokenDAO;
import com.airline.airlinebackend.dao.LoginAttemptDAO;
import com.airline.airlinebackend.dao.RememberMeTokenDAO;
import com.airline.airlinebackend.dao.SessionDAO;
import com.airline.airlinebackend.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link SessionService}.
 */
@DisplayName("SessionService")
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionDAO sessionDAO;

    @Mock
    private RememberMeTokenDAO rememberMeTokenDAO;

    @Mock
    private LoginAttemptDAO loginAttemptDAO;

    @Mock
    private EmailConfirmationTokenDAO emailConfirmationTokenDAO;

    @Captor
    private ArgumentCaptor<Instant> cutoffCaptor;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                sessionDAO, rememberMeTokenDAO, loginAttemptDAO, emailConfirmationTokenDAO);
    }

    private void stubAllCounts(int sessions, int rememberMeTokens, int emailTokens, int loginAttempts) {
        given(sessionDAO.deleteExpired()).willReturn(sessions);
        given(rememberMeTokenDAO.deleteExpired()).willReturn(rememberMeTokens);
        given(emailConfirmationTokenDAO.deleteExpired()).willReturn(emailTokens);
        given(loginAttemptDAO.deleteOlderThen(any(Instant.class))).willReturn(loginAttempts);
    }

    @Nested
    @DisplayName("cleanupExpired()")
    class CleanupExpiredTests {

        @Test
        @DisplayName("should aggregate deletion counts from all four stores into CleanupResult")
        void cleanupExpired_allStoresHaveExpiredRows_aggregatesAllCounts() {
            // Arrange
            // Distinct counts prove no cross-mapping between DAO and result field
            stubAllCounts(3, 5, 2, 7);

            // Act
            SessionService.CleanupResult result = sessionService.cleanupExpired();

            // Assert
            assertSoftly(softly -> {
                softly.assertThat(result.sessionsDeleted).isEqualTo(3);
                softly.assertThat(result.rememberMeTokensDeleted).isEqualTo(5);
                softly.assertThat(result.emailTokensDeleted).isEqualTo(2);
                softly.assertThat(result.loginAttemptsDeleted).isEqualTo(7);
            });
        }

        @Test
        @DisplayName("should return zero counts when nothing is expired")
        void cleanupExpired_nothingExpired_allCountsZero() {
            // Arrange
            stubAllCounts(0, 0, 0, 0);

            // Act
            SessionService.CleanupResult result = sessionService.cleanupExpired();

            // Assert
            assertSoftly(softly -> {
                softly.assertThat(result.sessionsDeleted).isZero();
                softly.assertThat(result.rememberMeTokensDeleted).isZero();
                softly.assertThat(result.emailTokensDeleted).isZero();
                softly.assertThat(result.loginAttemptsDeleted).isZero();
            });
        }

        @Test
        @DisplayName("should invoke cleanup on all four DAOs exactly once")
        void cleanupExpired_callsAllFourCleanupMethods() {
            // Act
            sessionService.cleanupExpired();

            // Assert
            then(sessionDAO).should().deleteExpired();
            then(rememberMeTokenDAO).should().deleteExpired();
            then(emailConfirmationTokenDAO).should().deleteExpired();
            then(loginAttemptDAO).should().deleteOlderThen(any(Instant.class));
        }

        @Test
        @DisplayName("should pass a cutoff 24 hours in the past to deleteOlderThen")
        void cleanupExpired_passesCutoffTwentyFourHoursInThePast() {
            // Arrange
            Instant twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS);

            // Act
            sessionService.cleanupExpired();

            // Assert
            then(loginAttemptDAO).should().deleteOlderThen(cutoffCaptor.capture());
            assertThat(cutoffCaptor.getValue())
                    .isCloseTo(twentyFourHoursAgo, within(1, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("should propagate exception from DAO and stop further cleanup")
        void cleanupExpired_sessionDaoThrows_propagatesException() {
            // Arrange
            given(sessionDAO.deleteExpired()).willThrow(new RuntimeException("session cleanup failed"));

            // Act / Assert
            assertThatThrownBy(() -> sessionService.cleanupExpired())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("session cleanup failed");

            then(rememberMeTokenDAO).should(never()).deleteExpired();
            then(emailConfirmationTokenDAO).should(never()).deleteExpired();
            then(loginAttemptDAO).should(never()).deleteOlderThen(any(Instant.class));
        }
    }
}