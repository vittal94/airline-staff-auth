package unittests.security;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.security.CsrfTokenService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for {@link CsrfTokenService}.
 * <p>
 * Verifies CSRF token generation, validation, refresh, and clearing using mocked
 * servlet request/response objects. The service delegates cookie handling to
 * {@link com.airline.airlinebackend.util.CookieUtil} and configuration to
 * {@link AppConfig}, so assertions focus on the resulting HTTP headers and
 * validation outcomes.
 */
@DisplayName("CsrfTokenService")
@ExtendWith(MockitoExtension.class)
public class CsrfTokenServiceTest {

    private static final String CSRF_COOKIE_NAME = AppConfig.getInstance().getCsrfCookieName();
    private static final String CSRF_HEADER_NAME = AppConfig.getInstance().getCsrfHeaderName();
    private static final int SESSION_TIMEOUT_MINUTES = AppConfig.getInstance().getSessionTimeoutMinutes();
    private static final int EXPECTED_MAX_AGE_SECONDS = SESSION_TIMEOUT_MINUTES * 60;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpServletRequest request;

    private CsrfTokenService csrfTokenService;

    @BeforeEach
    void setUp() {
        csrfTokenService = new CsrfTokenService();
    }

    @Nested
    @DisplayName("generateToken()")
    class GenerateTokenTests {

        @Test
        @DisplayName("should return a non-blank token string")
        void generateToken_returnsNonBlankToken() {
            // Act
            String token = csrfTokenService.generateToken(response);

            // Assert
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("should set CSRF token in a non-HttpOnly cookie with configured max-age")
        void generateToken_setsCsrfCookie() {
            // Act
            String token = csrfTokenService.generateToken(response);

            // Assert
            ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
            then(response).should().addHeader(eq("Set-Cookie"), headerCaptor.capture());

            String setCookieHeader = headerCaptor.getValue();
            assertThat(setCookieHeader)
                    .startsWith(CSRF_COOKIE_NAME + "=" + token)
                    .contains("Path=/")
                    .contains("SameSite=" + AppConfig.getInstance().getSessionCookieSameSite())
                    .contains("Max-Age=" + EXPECTED_MAX_AGE_SECONDS)
                    .doesNotContain("HttpOnly");

            if (AppConfig.getInstance().isSessionCookieSecure()) {
                assertThat(setCookieHeader).contains("Secure");
            }
        }
    }

    @Nested
    @DisplayName("refreshToken()")
    class RefreshTokenTests {

        @Test
        @DisplayName("should generate and set a new CSRF token")
        void refreshToken_generatesNewToken() {
            // Act
            String token = csrfTokenService.refreshToken(response);

            // Assert
            assertThat(token).isNotBlank();
            then(response).should().addHeader(eq("Set-Cookie"), anyString());
        }
    }

    @Nested
    @DisplayName("clearToken()")
    class ClearTokenTests {

        @Test
        @DisplayName("should add a Set-Cookie header that deletes the CSRF cookie")
        void clearToken_deletesCsrfCookie() {
            // Act
            csrfTokenService.clearToken(response);

            // Assert
            ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
            then(response).should().addHeader(eq("Set-Cookie"), headerCaptor.capture());

            String setCookieHeader = headerCaptor.getValue();
            assertThat(setCookieHeader)
                    .startsWith(CSRF_COOKIE_NAME + "=")
                    .contains("Path=/")
                    .contains("Max-Age=0");
        }
    }

    @Nested
    @DisplayName("validateToken()")
    class ValidateTokenTests {

        @Test
        @DisplayName("should return true when cookie token matches header token")
        void validateToken_matchingTokens_returnsTrue() {
            // Arrange
            String token = "matching-csrf-token";
            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(CSRF_COOKIE_NAME, token)
            });
            given(request.getHeader(CSRF_HEADER_NAME)).willReturn(token);

            // Act
            boolean result = csrfTokenService.validateToken(request);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when cookie token differs from header token")
        void validateToken_mismatchedTokens_returnsFalse() {
            // Arrange
            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(CSRF_COOKIE_NAME, "cookie-token")
            });
            given(request.getHeader(CSRF_HEADER_NAME)).willReturn("header-token");

            // Act
            boolean result = csrfTokenService.validateToken(request);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when CSRF cookie is missing")
        void validateToken_missingCookie_returnsFalse() {
            // Arrange
            given(request.getCookies()).willReturn(null);
            given(request.getHeader(CSRF_HEADER_NAME)).willReturn("header-token");

            // Act
            boolean result = csrfTokenService.validateToken(request);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when CSRF header is missing")
        void validateToken_missingHeader_returnsFalse() {
            // Arrange
            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(CSRF_COOKIE_NAME, "cookie-token")
            });
            given(request.getHeader(CSRF_HEADER_NAME)).willReturn(null);

            // Act
            boolean result = csrfTokenService.validateToken(request);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when both cookie and header are missing")
        void validateToken_bothMissing_returnsFalse() {
            // Arrange
            given(request.getCookies()).willReturn(null);
            given(request.getHeader(CSRF_HEADER_NAME)).willReturn(null);

            // Act
            boolean result = csrfTokenService.validateToken(request);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for equal-length tokens that differ in content")
        void validateToken_equalLengthMismatch_returnsFalse() {
            // Arrange
            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(CSRF_COOKIE_NAME, "token-value-a")
            });
            given(request.getHeader(CSRF_HEADER_NAME)).willReturn("token-value-b");

            // Act
            boolean result = csrfTokenService.validateToken(request);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when tokens have different lengths")
        void validateToken_differentLengthTokens_returnsFalse() {
            // Arrange
            given(request.getCookies()).willReturn(new Cookie[]{
                    new Cookie(CSRF_COOKIE_NAME, "short")
            });
            given(request.getHeader(CSRF_HEADER_NAME)).willReturn("a-much-longer-token-value");

            // Act
            boolean result = csrfTokenService.validateToken(request);

            // Assert
            assertThat(result).isFalse();
        }
    }
}
