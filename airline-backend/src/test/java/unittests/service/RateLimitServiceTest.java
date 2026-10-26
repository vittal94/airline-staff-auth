package com.airline.airlinebackend.service;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.dao.LoginAttemptDAO;
import com.airline.airlinebackend.exception.RateLimitExceededException;
import com.airline.airlinebackend.model.LoginAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link RateLimitService}.
 * <p>
 * These tests use Mockito to stub the {@link LoginAttemptDAO} and {@link AppConfig}
 * collaborators so the rate-limiting logic can be exercised in isolation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RateLimitService")
class RateLimitServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String IP_ADDRESS = "192.168.1.1";
    private static final int LOGIN_MAX_ATTEMPTS = 5;
    private static final int LOGIN_WINDOW_MINUTES = 15;
    private static final int IP_MAX_ATTEMPTS = 20;
    private static final int IP_WINDOW_MINUTES = 30;

    @Mock
    private LoginAttemptDAO loginAttemptDao;

    @Mock
    private AppConfig config;

    @Captor
    private ArgumentCaptor<LoginAttempt> loginAttemptCaptor;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        given(config.getRateLimitLoginMaxAttempts()).willReturn(LOGIN_MAX_ATTEMPTS);
        given(config.getRateLimitLoginWindowMinutes()).willReturn(LOGIN_WINDOW_MINUTES);
        given(config.getRateLimitIpMaxAttempts()).willReturn(IP_MAX_ATTEMPTS);
        given(config.getRateLimitIpWindowMinutes()).willReturn(IP_WINDOW_MINUTES);
        rateLimitService = new RateLimitService(loginAttemptDao, config);
    }

    @Nested
    @DisplayName("checkRateLimit()")
    class CheckRateLimitTests {

        @Test
        @DisplayName("should allow attempt when email and IP are both under their limits")
        void checkRateLimit_underLimits_doesNotThrow() {
            // Arrange
            given(loginAttemptDao.countFailedAttemptsByEmail(any(), any()))
                    .willReturn(LOGIN_MAX_ATTEMPTS - 1);
            given(loginAttemptDao.countFailedAttemptsByIpAddress(any(), any()))
                    .willReturn(IP_MAX_ATTEMPTS - 1);

            // Act & Assert
            assertThatCode(() -> rateLimitService.checkRateLimit(EMAIL, IP_ADDRESS))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw when failed attempts by email reach the limit")
        void checkRateLimit_emailAtLimit_throwsRateLimitExceeded() {
            // Arrange
            given(loginAttemptDao.countFailedAttemptsByEmail(any(), any())).willReturn(LOGIN_MAX_ATTEMPTS);

            // Act & Assert
            assertThatThrownBy(() -> rateLimitService.checkRateLimit(EMAIL, IP_ADDRESS))
                    .isInstanceOf(RateLimitExceededException.class)
                    .hasMessageContaining("Too many failed login attempts")
                    .satisfies(ex -> assertThat(((RateLimitExceededException) ex).getRetryAfterSeconds())
                            .isEqualTo(LOGIN_WINDOW_MINUTES * 60L));

            then(loginAttemptDao).should(never()).countFailedAttemptsByIpAddress(any(), any());
        }

        @Test
        @DisplayName("should throw when failed attempts by IP reach the limit")
        void checkRateLimit_ipAtLimit_throwsRateLimitExceeded() {
            // Arrange
            given(loginAttemptDao.countFailedAttemptsByEmail(any(), any())).willReturn(LOGIN_MAX_ATTEMPTS - 1);
            given(loginAttemptDao.countFailedAttemptsByIpAddress(any(), any())).willReturn(IP_MAX_ATTEMPTS);

            // Act & Assert
            assertThatThrownBy(() -> rateLimitService.checkRateLimit(EMAIL, IP_ADDRESS))
                    .isInstanceOf(RateLimitExceededException.class)
                    .hasMessageContaining("Too many failed login attempts from this IP")
                    .satisfies(ex -> assertThat(((RateLimitExceededException) ex).getRetryAfterSeconds())
                            .isEqualTo(IP_WINDOW_MINUTES * 60L));
        }

        @Test
        @DisplayName("should query DAOs with a time window based on configured minutes")
        void checkRateLimit_underLimits_passesCorrectWindowToDaos() {
            // Arrange
            Instant beforeCall = Instant.now().minus(1, ChronoUnit.SECONDS);
            given(loginAttemptDao.countFailedAttemptsByEmail(any(), any())).willReturn(0);
            given(loginAttemptDao.countFailedAttemptsByIpAddress(any(), any())).willReturn(0);

            // Act
            rateLimitService.checkRateLimit(EMAIL, IP_ADDRESS);

            // Assert
            Instant afterCall = Instant.now().plus(1, ChronoUnit.SECONDS);
            then(loginAttemptDao).should().countFailedAttemptsByEmail(
                    argThat(email -> email.equals(EMAIL)),
                    argThat(instant -> instant.isAfter(beforeCall.minus(LOGIN_WINDOW_MINUTES, ChronoUnit.MINUTES))
                            && instant.isBefore(afterCall.minus(LOGIN_WINDOW_MINUTES, ChronoUnit.MINUTES)))
            );
            then(loginAttemptDao).should().countFailedAttemptsByIpAddress(
                    argThat(ip -> ip.equals(IP_ADDRESS)),
                    argThat(instant -> instant.isAfter(beforeCall.minus(IP_WINDOW_MINUTES, ChronoUnit.MINUTES))
                            && instant.isBefore(afterCall.minus(IP_WINDOW_MINUTES, ChronoUnit.MINUTES)))
            );
        }
    }

    @Nested
    @DisplayName("recordFailedAttempt()")
    class RecordFailedAttemptTests {

        @Test
        @DisplayName("should persist a failed login attempt")
        void recordFailedAttempt_savesFailedAttempt() {
            // Act
            rateLimitService.recordFailedAttempt(EMAIL, IP_ADDRESS);

            // Assert
            then(loginAttemptDao).should().save(loginAttemptCaptor.capture());
            LoginAttempt captured = loginAttemptCaptor.getValue();
            assertThat(captured.getEmail()).isEqualTo(EMAIL);
            assertThat(captured.getIpAddress()).isEqualTo(IP_ADDRESS);
            assertThat(captured.isSuccess()).isFalse();
            assertThat(captured.getAttemptedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("recordSuccessfulLogin()")
    class RecordSuccessfulLoginTests {

        @Test
        @DisplayName("should persist a successful login attempt")
        void recordSuccessfulLogin_savesSuccessfulAttempt() {
            // Act
            rateLimitService.recordSuccessfulLogin(EMAIL, IP_ADDRESS);

            // Assert
            then(loginAttemptDao).should().save(loginAttemptCaptor.capture());
            LoginAttempt captured = loginAttemptCaptor.getValue();
            assertThat(captured.getEmail()).isEqualTo(EMAIL);
            assertThat(captured.getIpAddress()).isEqualTo(IP_ADDRESS);
            assertThat(captured.isSuccess()).isTrue();
            assertThat(captured.getAttemptedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
        }
    }
}
