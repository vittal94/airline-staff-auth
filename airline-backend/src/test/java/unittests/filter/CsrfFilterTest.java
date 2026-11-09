package unittests.filter;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.filter.CsrfFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link CsrfFilter}.
 */
@DisplayName("CsrfFilter")
@ExtendWith(MockitoExtension.class)
class CsrfFilterTest {

    // Cookie/header names are derived from AppConfig — the same configuration
    // source the filter and CsrfTokenService read at runtime.
    private static final String CSRF_COOKIE_NAME = AppConfig.getInstance().getCsrfCookieName();
    private static final String CSRF_HEADER_NAME = AppConfig.getInstance().getCsrfHeaderName();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Mock
    private PrintWriter writer;

    @Captor
    private ArgumentCaptor<String> stringCaptor;

    private CsrfFilter csrfFilter;

    @BeforeEach
    void setUp() throws ServletException {
        csrfFilter = new CsrfFilter();
        // csrfTokenService is assigned only in init(); calling doFilter() without it NPEs
        csrfFilter.init(null);
    }

    private Cookie[] csrfCookieWithValue(String value) {
        return new Cookie[]{new Cookie(CSRF_COOKIE_NAME, value)};
    }

    @Nested
    @DisplayName("doFilter() - safe methods")
    class SafeMethodTests {

        @ParameterizedTest(name = "method: {0}")
        @ValueSource(strings = {"GET", "HEAD", "OPTIONS", "get"})
        @DisplayName("should issue a CSRF cookie, continue the chain, and set no status for safe methods")
        void doFilter_safeMethod_setsCsrfCookieAndContinuesChain(String method) throws Exception {
            // Arrange
            given(request.getMethod()).willReturn(method);
            given(request.getRequestURI()).willReturn("/api/users");

            // Act
            csrfFilter.doFilter(request, response, chain);

            // Assert
            then(response).should().addHeader(eq("Set-Cookie"), stringCaptor.capture());
            assertThat(stringCaptor.getValue()).startsWith(CSRF_COOKIE_NAME + "=");
            then(chain).should().doFilter(request, response);
            then(response).should(never()).setStatus(anyInt());
        }
    }

    @Nested
    @DisplayName("doFilter() - exempt endpoints")
    class ExemptEndpointTests {

        @ParameterizedTest(name = "endpoint: {0}")
        @ValueSource(strings = {
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/confirm-email",
                "/api/auth/resend-confirmation",
                "/api/health"})
        @DisplayName("should issue a CSRF cookie and continue the chain without validation for exempt endpoints")
        void doFilter_exemptEndpoint_setsCsrfCookieAndContinuesChain(String path) throws Exception {
            // Arrange
            given(request.getMethod()).willReturn("POST");
            given(request.getRequestURI()).willReturn(path);

            // Act
            csrfFilter.doFilter(request, response, chain);

            // Assert
            then(response).should().addHeader(eq("Set-Cookie"), stringCaptor.capture());
            assertThat(stringCaptor.getValue()).startsWith(CSRF_COOKIE_NAME + "=");
            then(chain).should().doFilter(request, response);
            then(response).should(never()).setStatus(anyInt());
        }

        @Test
        @DisplayName("should block a POST to a path that only contains an exempt endpoint as a substring")
        void doFilter_pathContainingExemptSubstring_requiresValidationAndBlocks() throws Exception {
            // Arrange
            given(request.getMethod()).willReturn("POST");
            given(request.getRequestURI()).willReturn("/api/auth/login-extra");
            given(request.getCookies()).willReturn(null);
            given(response.getWriter()).willReturn(writer);

            // Act
            csrfFilter.doFilter(request, response, chain);

            // Assert
            then(response).should().setStatus(HttpServletResponse.SC_FORBIDDEN);
            then(chain).should(never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        }
    }

    @Nested
    @DisplayName("doFilter() - token validation")
    class ValidationTests {

        @Test
        @DisplayName("should refresh the CSRF token and continue the chain when cookie and header tokens match")
        void doFilter_matchingCookieAndHeaderTokens_refreshesTokenAndContinuesChain() throws Exception {
            // Arrange
            given(request.getMethod()).willReturn("POST");
            given(request.getRequestURI()).willReturn("/api/users");
            given(request.getCookies()).willReturn(csrfCookieWithValue("valid-csrf-token"));
            given(request.getHeader(CSRF_HEADER_NAME)).willReturn("valid-csrf-token");

            // Act
            csrfFilter.doFilter(request, response, chain);

            // Assert
            then(response).should().addHeader(eq("Set-Cookie"), stringCaptor.capture());
            assertThat(stringCaptor.getValue()).startsWith(CSRF_COOKIE_NAME + "=");
            then(chain).should().doFilter(request, response);
            then(response).should(never()).setStatus(anyInt());
        }

        @Test
        @DisplayName("should respond 403 with a JSON error and block the chain when the CSRF cookie is missing")
        void doFilter_missingCsrfCookie_blockedWith403() throws Exception {
            // Arrange
            given(request.getMethod()).willReturn("POST");
            given(request.getRequestURI()).willReturn("/api/users");
            given(request.getCookies()).willReturn(null);
            given(response.getWriter()).willReturn(writer);

            // Act
            csrfFilter.doFilter(request, response, chain);

            // Assert
            verifyBlockedWith403JsonError();
        }

        @Test
        @DisplayName("should respond 403 with a JSON error and block the chain when the CSRF header is missing")
        void doFilter_missingCsrfHeader_blockedWith403() throws Exception {
            // Arrange
            given(request.getMethod()).willReturn("POST");
            given(request.getRequestURI()).willReturn("/api/users");
            given(request.getCookies()).willReturn(csrfCookieWithValue("valid-csrf-token"));
            given(request.getHeader(CSRF_HEADER_NAME)).willReturn(null);
            given(response.getWriter()).willReturn(writer);

            // Act
            csrfFilter.doFilter(request, response, chain);

            // Assert
            verifyBlockedWith403JsonError();
        }

        @Test
        @DisplayName("should respond 403 with a JSON error and block the chain when tokens do not match")
        void doFilter_mismatchedTokens_blockedWith403() throws Exception {
            // Arrange
            given(request.getMethod()).willReturn("POST");
            given(request.getRequestURI()).willReturn("/api/users");
            given(request.getCookies()).willReturn(csrfCookieWithValue("cookie-token"));
            given(request.getHeader(CSRF_HEADER_NAME)).willReturn("header-token");
            given(response.getWriter()).willReturn(writer);

            // Act
            csrfFilter.doFilter(request, response, chain);

            // Assert
            verifyBlockedWith403JsonError();
        }

        private void verifyBlockedWith403JsonError() throws Exception {
            then(response).should().setStatus(HttpServletResponse.SC_FORBIDDEN);
            then(response).should().setContentType("application/json");
            then(writer).should().write(stringCaptor.capture());
            assertThat(stringCaptor.getValue())
                    .contains("CSRF_VALIDATION_FAILED")
                    .contains("CSRF token validation failed");
            then(response).should(never()).addHeader(eq("Set-Cookie"), anyString());
            then(chain).should(never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        }
    }
}
