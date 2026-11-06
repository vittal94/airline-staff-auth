package unittests.filter;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.filter.CorsFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link CorsFilter}.
 */
@DisplayName("CorsFilter")
@ExtendWith(MockitoExtension.class)
class CorsFilterTest {

    // The filter must take the CSRF header name from AppConfig, not hardcode it,
    // so the expected header value is derived from the same configuration source.
    private static final String ALLOW_HEADERS =
            "Content-Type, " + AppConfig.getInstance().getCsrfHeaderName() + ", Authorization";
    private static final String ALLOW_METHODS = "GET, POST, PUT, DELETE, OPTIONS";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private CorsFilter corsFilter;

    @BeforeEach
    void setUp() throws ServletException {
        corsFilter = new CorsFilter();
        // config is assigned only in init(); calling doFilter() without it NPEs on the allowed-origin path
        corsFilter.init(null);
    }

    private void verifyCorsHeadersApplied(String origin) {
        then(response).should().setHeader("Access-Control-Allow-Origin", origin);
        then(response).should().setHeader("Access-Control-Allow-Credentials", "true");
        then(response).should().setHeader("Access-Control-Allow-Methods", ALLOW_METHODS);
        then(response).should().setHeader("Access-Control-Allow-Headers", ALLOW_HEADERS);
        then(response).should().setHeader("Access-Control-Max-Age", "3600");
    }

    @Nested
    @DisplayName("doFilter() - simple requests")
    class SimpleRequestTests {

        @ParameterizedTest(name = "origin: {0}")
        @ValueSource(strings = {
                "http://localhost", "https://localhost",
                "http://localhost:5173", "https://localhost:8443"})
        @DisplayName("should set all five CORS headers and continue the chain for an allowed localhost origin")
        void doFilter_allowedOrigin_setsCorsHeadersAndContinuesChain(String origin) throws Exception {
            // Arrange
            given(request.getHeader("Origin")).willReturn(origin);
            given(request.getMethod()).willReturn("GET");

            // Act
            corsFilter.doFilter(request, response, chain);

            // Assert
            verifyCorsHeadersApplied(origin);
            then(response).should(never()).setStatus(anyInt());
            then(chain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("should set no CORS headers but still continue the chain when Origin is absent")
        void doFilter_missingOrigin_skipsCorsHeadersAndContinuesChain() throws Exception {
            // Arrange
            given(request.getHeader("Origin")).willReturn(null);
            given(request.getMethod()).willReturn("GET");

            // Act
            corsFilter.doFilter(request, response, chain);

            // Assert
            then(response).should(never()).setHeader(startsWith("Access-Control"), anyString());
            then(chain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("should set no CORS headers but still continue the chain for a foreign origin")
        void doFilter_disallowedOrigin_skipsCorsHeadersAndContinuesChain() throws Exception {
            // Arrange
            given(request.getHeader("Origin")).willReturn("https://evil.com");
            given(request.getMethod()).willReturn("GET");

            // Act
            corsFilter.doFilter(request, response, chain);

            // Assert
            then(response).should(never()).setHeader(startsWith("Access-Control"), anyString());
            then(chain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("should not set a response status and continue the chain when the method is not OPTIONS")
        void doFilter_nonOptionsMethod_doesNotSetStatusAndContinuesChain() throws Exception {
            // Arrange
            given(request.getHeader("Origin")).willReturn("http://localhost");
            given(request.getMethod()).willReturn("POST");

            // Act
            corsFilter.doFilter(request, response, chain);

            // Assert
            then(response).should(never()).setStatus(anyInt());
            then(chain).should().doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("doFilter() - preflight (OPTIONS)")
    class PreflightTests {

        @Test
        @DisplayName("should set all CORS headers with SC_OK and skip the chain for an allowed preflight")
        void doFilter_optionsFromAllowedOrigin_setsCorsHeadersStatusAndSkipsChain() throws Exception {
            // Arrange
            given(request.getHeader("Origin")).willReturn("http://localhost:5173");
            given(request.getMethod()).willReturn("OPTIONS");

            // Act
            corsFilter.doFilter(request, response, chain);

            // Assert
            verifyCorsHeadersApplied("http://localhost:5173");
            then(response).should().setStatus(HttpServletResponse.SC_OK);
            then(chain).should(never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        }

        @Test
        @DisplayName("should set SC_OK without CORS headers and skip the chain for a foreign preflight")
        void doFilter_optionsFromDisallowedOrigin_setsStatusAndSkipsChain() throws Exception {
            // Arrange
            given(request.getHeader("Origin")).willReturn("https://evil.com");
            given(request.getMethod()).willReturn("OPTIONS");

            // Act
            corsFilter.doFilter(request, response, chain);

            // Assert
            then(response).should(never()).setHeader(startsWith("Access-Control"), anyString());
            then(response).should().setStatus(HttpServletResponse.SC_OK);
            then(chain).should(never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        }

        @ParameterizedTest(name = "method: {0}")
        @ValueSource(strings = {"OPTIONS", "options"})
        @DisplayName("should treat the request method case-insensitively as a preflight and skip the chain")
        void doFilter_caseInsensitiveOptions_setsStatusAndSkipsChain(String method) throws Exception {
            // Arrange
            given(request.getMethod()).willReturn(method);

            // Act
            corsFilter.doFilter(request, response, chain);

            // Assert
            then(response).should().setStatus(HttpServletResponse.SC_OK);
            then(chain).should(never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        }
    }

    @Nested
    @DisplayName("doFilter() - origin matching")
    class OriginMatchingTests {

        @Test
        @DisplayName("should reject the literal Origin: null string (sandboxed iframe) as a foreign origin")
        void doFilter_nullOriginString_skipsCorsHeadersAndContinuesChain() throws Exception {
            // Arrange
            given(request.getHeader("Origin")).willReturn("null");
            given(request.getMethod()).willReturn("GET");

            // Act
            corsFilter.doFilter(request, response, chain);

            // Assert
            then(response).should(never()).setHeader(startsWith("Access-Control"), anyString());
            then(chain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("should not treat a localhost-lookalike domain as an allowed origin")
        void doFilter_localhostLookalikeOrigin_rejected() throws Exception {
            // Arrange
            given(request.getHeader("Origin")).willReturn("http://localhost.evil.com");
            given(request.getMethod()).willReturn("GET");

            // Act
            corsFilter.doFilter(request, response, chain);

            // Assert
            then(response).should(never()).setHeader(startsWith("Access-Control"), anyString());
            then(chain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("should set Vary: Origin whenever CORS headers are applied")
        void doFilter_corsHeadersApplied_setsVaryOrigin() throws Exception {
            // Arrange
            given(request.getHeader("Origin")).willReturn("http://localhost");
            given(request.getMethod()).willReturn("GET");

            // Act
            corsFilter.doFilter(request, response, chain);

            // Assert
            then(response).should().setHeader("Vary", "Origin");
        }
    }
}