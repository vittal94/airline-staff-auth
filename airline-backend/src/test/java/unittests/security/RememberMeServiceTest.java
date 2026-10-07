package unittests.security;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.dao.RememberMeTokenDAO;
import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.model.RememberMeToken;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.enums.Role;
import com.airline.airlinebackend.model.enums.UserStatus;
import com.airline.airlinebackend.security.PasswordEncoder;
import com.airline.airlinebackend.security.RememberMeService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link RememberMeService}.
 * <p>
 * Verifies remember-me token creation, validation/rotation, logout cleanup, and
 * user-scoped token revocation. DAOs and configuration are mocked; static utilities
 * for token generation, hashing, and cookie handling are exercised indirectly through
 * their observable effects.
 */
@DisplayName("RememberMeService")
@ExtendWith(MockitoExtension.class)
class RememberMeServiceTest {

    private static final String REMEMBER_ME_COOKIE_NAME = "AIRLINE_REMEMBER_ME";
    private static final int REMEMBER_ME_DURATION_HOURS = 24;
    private static final int EXPECTED_MAX_AGE_SECONDS = REMEMBER_ME_DURATION_HOURS * 3600;
    private static final String CLIENT_IP = "127.0.0.1";
    private static final String USER_AGENT = "Mozilla/5.0";

    @Mock
    private RememberMeTokenDAO tokenDAO;

    @Mock
    private UserDAO userDAO;

    @Mock
    private AppConfig appConfig;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private RememberMeService rememberMeService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        rememberMeService = new RememberMeService(tokenDAO, userDAO, appConfig);
        userId = UUID.randomUUID();
    }

    private void stubRememberMeConfig() {
        lenient().when(appConfig.getRememberMeCookieName()).thenReturn(REMEMBER_ME_COOKIE_NAME);
        lenient().when(appConfig.getRememberMeDurationHours()).thenReturn(REMEMBER_ME_DURATION_HOURS);
    }

    private void stubCookieConfig() {
        lenient().when(appConfig.isSessionCookieSecure()).thenReturn(false);
        lenient().when(appConfig.getSessionCookieSameSite()).thenReturn("Strict");
    }

    @Nested
    @DisplayName("createRememberMeToken()")
    class CreateRememberMeTokenTests {

        @Test
        @DisplayName("should save a token and set a secure series:token cookie")
        void createRememberMeToken_validUser_savesTokenAndSetsCookie() {
            // Arrange
            stubRememberMeConfig();
            stubCookieConfig();
            given(request.getHeader("X-Forwarded-For")).willReturn(null);
            given(request.getHeader("X-Real-IP")).willReturn(null);
            given(request.getRemoteAddr()).willReturn(CLIENT_IP);
            given(request.getHeader("User-Agent")).willReturn(USER_AGENT);

            // Act
            rememberMeService.createRememberMeToken(request, response, userId);

            // Assert
            ArgumentCaptor<RememberMeToken> tokenCaptor = ArgumentCaptor.forClass(RememberMeToken.class);
            then(tokenDAO).should().save(tokenCaptor.capture());
            RememberMeToken savedToken = tokenCaptor.getValue();

            assertThat(savedToken.getSeries()).isNotBlank();
            assertThat(savedToken.getUserId()).isEqualTo(userId);
            assertThat(savedToken.getTokenHash()).isNotBlank();
            assertThat(savedToken.getIpAddress()).isEqualTo(CLIENT_IP);
            assertThat(savedToken.getUserAgent()).isEqualTo(USER_AGENT);
            assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());

            ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
            then(response).should().addHeader(eq("Set-Cookie"), headerCaptor.capture());
            String setCookieHeader = headerCaptor.getValue();
            assertThat(setCookieHeader)
                    .startsWith(REMEMBER_ME_COOKIE_NAME + "=")
                    .contains("Path=/")
                    .contains("HttpOnly")
                    .contains("SameSite=Strict")
                    .contains("Max-Age=" + EXPECTED_MAX_AGE_SECONDS);
        }

        @Test
        @DisplayName("should use X-Forwarded-For header when present")
        void createRememberMeToken_withXForwardedFor_usesOriginalClientIp() {
            // Arrange
            stubRememberMeConfig();
            stubCookieConfig();
            given(request.getHeader("X-Forwarded-For")).willReturn("203.0.113.42, 10.0.0.1");
            given(request.getHeader("User-Agent")).willReturn(USER_AGENT);

            // Act
            rememberMeService.createRememberMeToken(request, response, userId);

            // Assert
            ArgumentCaptor<RememberMeToken> tokenCaptor = ArgumentCaptor.forClass(RememberMeToken.class);
            then(tokenDAO).should().save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().getIpAddress()).isEqualTo("203.0.113.42");
        }
    }

    @Nested
    @DisplayName("validateAndRotate()")
    class ValidateAndRotateTests {

        @Test
        @DisplayName("should return empty when remember-me cookie is missing")
        void validateAndRotate_missingCookie_returnsEmpty() {
            // Arrange
            given(appConfig.getRememberMeCookieName()).willReturn(REMEMBER_ME_COOKIE_NAME);
            given(request.getCookies()).willReturn(null);

            // Act
            Optional<User> result = rememberMeService.validateAndRotate(request, response);

            // Assert
            assertThat(result).isEmpty();
            then(tokenDAO).should(never()).findBySeries(anyString());
            then(response).should(never()).addHeader(eq("Set-Cookie"), anyString());
        }

        @Test
        @DisplayName("should clear cookie and return empty for malformed cookie value")
        void validateAndRotate_malformedCookie_clearsCookieAndReturnsEmpty() {
            // Arrange
            stubRememberMeConfig();
            stubCookieConfig();
            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(REMEMBER_ME_COOKIE_NAME, "malformed-value")
            });

            // Act
            Optional<User> result = rememberMeService.validateAndRotate(request, response);

            // Assert
            assertThat(result).isEmpty();
            then(tokenDAO).should(never()).findBySeries(anyString());
            thenCookieShouldBeCleared();
        }

        @Test
        @DisplayName("should clear cookie and return empty when series is unknown")
        void validateAndRotate_unknownSeries_clearsCookieAndReturnsEmpty() {
            // Arrange
            stubRememberMeConfig();
            stubCookieConfig();
            String series = UUID.randomUUID().toString();
            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(REMEMBER_ME_COOKIE_NAME, series + ":token-value")
            });
            given(tokenDAO.findBySeries(series)).willReturn(Optional.empty());

            // Act
            Optional<User> result = rememberMeService.validateAndRotate(request, response);

            // Assert
            assertThat(result).isEmpty();
            then(tokenDAO).should().findBySeries(series);
            then(tokenDAO).should(never()).deleteByUserId(any(UUID.class));
            thenCookieShouldBeCleared();
        }

        @Test
        @DisplayName("should delete expired token by series and clear cookie")
        void validateAndRotate_expiredToken_deletesBySeriesAndClearsCookie() {
            // Arrange
            stubRememberMeConfig();
            stubCookieConfig();
            String series = UUID.randomUUID().toString();
            String token = "plain-token";
            RememberMeToken expiredToken = buildToken(series, token, Instant.now().minus(1, ChronoUnit.HOURS));

            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(REMEMBER_ME_COOKIE_NAME, series + ":" + token)
            });
            given(tokenDAO.findBySeries(series)).willReturn(Optional.of(expiredToken));

            // Act
            Optional<User> result = rememberMeService.validateAndRotate(request, response);

            // Assert
            assertThat(result).isEmpty();
            then(tokenDAO).should().deleteBySeries(series);
            then(tokenDAO).should(never()).deleteByUserId(any(UUID.class));
            thenCookieShouldBeCleared();
        }

        @Test
        @DisplayName("should detect token theft, delete all user tokens, and clear cookie")
        void validateAndRotate_tokenMismatch_deletesAllUserTokensAndClearsCookie() {
            // Arrange
            stubRememberMeConfig();
            stubCookieConfig();
            String series = UUID.randomUUID().toString();
            String validToken = "valid-token";
            String stolenToken = "stolen-token";
            RememberMeToken storedToken = buildToken(series, validToken, Instant.now().plus(1, ChronoUnit.HOURS));

            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(REMEMBER_ME_COOKIE_NAME, series + ":" + stolenToken)
            });
            given(tokenDAO.findBySeries(series)).willReturn(Optional.of(storedToken));

            // Act
            Optional<User> result = rememberMeService.validateAndRotate(request, response);

            // Assert
            assertThat(result).isEmpty();
            then(tokenDAO).should().deleteByUserId(userId);
            then(tokenDAO).should(never()).deleteBySeries(anyString());
            thenCookieShouldBeCleared();
        }

        @Test
        @DisplayName("should rotate valid token and return the associated user")
        void validateAndRotate_validToken_rotatesTokenAndReturnsUser() {
            // Arrange
            stubRememberMeConfig();
            stubCookieConfig();
            String series = UUID.randomUUID().toString();
            String token = "plain-token";
            RememberMeToken storedToken = buildToken(series, token, Instant.now().plus(1, ChronoUnit.HOURS));
            User expectedUser = buildUser(userId);

            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(REMEMBER_ME_COOKIE_NAME, series + ":" + token)
            });
            given(tokenDAO.findBySeries(series)).willReturn(Optional.of(storedToken));
            given(userDAO.findById(userId)).willReturn(Optional.of(expectedUser));

            // Act
            Optional<User> result = rememberMeService.validateAndRotate(request, response);

            // Assert
            assertThat(result).isPresent().contains(expectedUser);

            ArgumentCaptor<String> tokenHashCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Instant> expiresCaptor = ArgumentCaptor.forClass(Instant.class);
            then(tokenDAO).should().update(eq(series), tokenHashCaptor.capture(), expiresCaptor.capture());
            assertThat(tokenHashCaptor.getValue()).isNotBlank().isNotEqualTo(storedToken.getTokenHash());
            assertThat(expiresCaptor.getValue()).isAfter(Instant.now());

            ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
            then(response).should().addHeader(eq("Set-Cookie"), headerCaptor.capture());
            String setCookieHeader = headerCaptor.getValue();
            assertThat(setCookieHeader)
                    .startsWith(REMEMBER_ME_COOKIE_NAME + "=" + series + ":")
                    .contains("Max-Age=" + EXPECTED_MAX_AGE_SECONDS);
        }
    }

    @Nested
    @DisplayName("clearRememberMe()")
    class ClearRememberMeTests {

        @Test
        @DisplayName("should delete token by series and clear cookie when cookie is present")
        void clearRememberMe_withCookie_deletesBySeriesAndClearsCookie() {
            // Arrange
            stubRememberMeConfig();
            stubCookieConfig();
            String series = UUID.randomUUID().toString();
            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(REMEMBER_ME_COOKIE_NAME, series + ":token-value")
            });

            // Act
            rememberMeService.clearRememberMe(request, response);

            // Assert
            then(tokenDAO).should().deleteBySeries(series);
            thenCookieShouldBeCleared();
        }

        @Test
        @DisplayName("should delete by series and clear cookie even for malformed cookie")
        void clearRememberMe_malformedCookie_deletesBySeriesAndClearsCookie() {
            // Arrange
            stubRememberMeConfig();
            stubCookieConfig();
            String series = UUID.randomUUID().toString();
            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(REMEMBER_ME_COOKIE_NAME, series)
            });

            // Act
            rememberMeService.clearRememberMe(request, response);

            // Assert
            then(tokenDAO).should().deleteBySeries(series);
            thenCookieShouldBeCleared();
        }

        @Test
        @DisplayName("should only clear cookie when remember-me cookie is missing")
        void clearRememberMe_noCookie_onlyClearsCookie() {
            // Arrange
            stubRememberMeConfig();
            stubCookieConfig();
            given(request.getCookies()).willReturn(null);

            // Act
            rememberMeService.clearRememberMe(request, response);

            // Assert
            then(tokenDAO).should(never()).deleteBySeries(anyString());
            thenCookieShouldBeCleared();
        }
    }

    @Nested
    @DisplayName("clearAllUserTokens()")
    class ClearAllUserTokensTests {

        @Test
        @DisplayName("should delete all remember-me tokens for the given user")
        void clearAllUserTokens_validUserId_deletesAllTokens() {
            // Act
            rememberMeService.clearAllUserTokens(userId);

            // Assert
            then(tokenDAO).should().deleteByUserId(userId);
        }
    }

    private void thenCookieShouldBeCleared() {
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        then(response).should().addHeader(eq("Set-Cookie"), headerCaptor.capture());
        String setCookieHeader = headerCaptor.getValue();
        assertThat(setCookieHeader)
                .startsWith(REMEMBER_ME_COOKIE_NAME + "=")
                .contains("Max-Age=0");
    }

    private RememberMeToken buildToken(String series, String plainToken, Instant expiresAt) {
        RememberMeToken token = new RememberMeToken();
        token.setSeries(series);
        token.setUserId(userId);
        token.setTokenHash(PasswordEncoder.hashToken(plainToken));
        token.setIpAddress(CLIENT_IP);
        token.setUserAgent(USER_AGENT);
        token.setCreatedAt(Instant.now());
        token.setLastUsedAt(Instant.now());
        token.setExpiresAt(expiresAt);
        return token;
    }

    private User buildUser(UUID id) {
        return User.builder()
                .id(id)
                .email("user@example.com")
                .name("Test User")
                .role(Role.CUSTOMER_MANAGER)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
