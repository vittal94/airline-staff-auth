package unittests.filter;

import com.airline.airlinebackend.filter.SecurityFilter;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.enums.Role;
import com.airline.airlinebackend.security.PostgresSessionStore;
import com.airline.airlinebackend.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link SecurityFilter}.
 *
 * The filter authenticates via {@link AuthService} (session first, then
 * remember-me) and authorizes by role per endpoint prefix. These tests mock
 * the two collaborators and drive everything through {@code doFilter},
 * verifying status codes, error bodies, security headers, and the request
 * attribute the servlets downstream rely on.
 */
@DisplayName("SecurityFilter")
@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Mock
    private PrintWriter writer;

    @Mock
    private PostgresSessionStore sessionStore;

    @Mock
    private AuthService authService;

    @InjectMocks
    private SecurityFilter filter;

    @Captor
    private ArgumentCaptor<String> bodyCaptor;

    private static final User ADMIN = userWithRole(Role.ADMIN);
    private static final User FLIGHT_MANAGER = userWithRole(Role.FLIGHT_MANAGER);
    private static final User CUSTOMER_MANAGER = userWithRole(Role.CUSTOMER_MANAGER);

    @BeforeEach
    void setUp() {
        //Mockito's default for getContextPath() is null; a real request always returns a string
        given(request.getContextPath()).willReturn("");
    }

    // -- fixtures -----------------------------------------------------------

    private static User userWithRole(Role role) {
        return User.builder().role(role).build();
    }

    private void requestTo(String uri) {
        given(request.getRequestURI()).willReturn(uri);
    }

    private void givenAuthenticatedSession(User user) {
        given(authService.getCurrentUser(request, response, sessionStore))
                .willReturn(Optional.of(user));
    }

    private void givenNoSessionButRememberMe(User user) {
        given(authService.getCurrentUser(request, response, sessionStore))
                .willReturn(Optional.empty());
        given(authService.tryToRememberMeLogin(request, response, sessionStore))
                .willReturn(Optional.of(user));
    }

    private void givenAnonymous() throws Exception {
        given(authService.getCurrentUser(request, response, sessionStore))
                .willReturn(Optional.empty());
        given(authService.tryToRememberMeLogin(request, response, sessionStore))
                .willReturn(Optional.empty());
        given(response.getWriter()).willReturn(writer);
    }

    // -- 1. public endpoints -----------------------------------------------

    @Nested
    @DisplayName("public endpoints")
    class PublicEndpointTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/confirm-email",
                "/api/auth/resend-confirmation",
                "/api/health",
                "/api/home"
        })
        @DisplayName("public endpoints pass through without authentication")
        void publicEndpointsPassThrough(String path) throws Exception {
            requestTo(path);

            filter.doFilter(request, response, chain);

            then(chain).should().doFilter(request, response);
            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("a protected endpoint that merely shares a prefix with a public one is NOT public")
        void prefixedPathOfPublicEndpointIsNotPublic() throws Exception {
            requestTo("/api/auth/login-history");
            givenAnonymous();

            filter.doFilter(request, response, chain);

            //unauthenticated → blocked, proving the near-identical path is not treated as public
            then(response).should().setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            then(chain).shouldHaveNoInteractions();
        }
    }

    // -- 2. authentication --------------------------------------------------

    @Nested
    @DisplayName("authentication")
    class AuthenticationTests {

        @Test
        @DisplayName("authenticated user with sufficient role reaches the chain and is stored as request attribute")
        void authenticatedUserReachesChainWithUserAttribute() throws Exception {
            requestTo("/api/cabinet");
            givenAuthenticatedSession(ADMIN);

            filter.doFilter(request, response, chain);

            then(chain).should().doFilter(request, response);
            then(request).should().setAttribute("user", ADMIN);
        }

        @Test
        @DisplayName("no session and no remember-me token → 401 with UNAUTHORIZED error body")
        void anonymousRequestIsRejectedWith401() throws Exception {
            requestTo("/api/cabinet");
            givenAnonymous();

            filter.doFilter(request, response, chain);

            then(response).should().setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            then(response).should().setContentType("application/json; charset=UTF-8");
            then(writer).should().write(bodyCaptor.capture());
            assertThat(bodyCaptor.getValue())
                    .contains("UNAUTHORIZED")
                    .contains("Authentication required");
            then(chain).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("empty session falls back to remember-me authentication")
        void emptySessionFallsBackToRememberMe() throws Exception {
            requestTo("/api/cabinet");
            givenNoSessionButRememberMe(FLIGHT_MANAGER);

            filter.doFilter(request, response, chain);

            then(authService).should().getCurrentUser(request, response, sessionStore);
            then(authService).should().tryToRememberMeLogin(request, response, sessionStore);
            then(request).should().setAttribute("user", FLIGHT_MANAGER);
            then(chain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("existing session skips remember-me entirely")
        void sessionLoginSkipsRememberMe() throws Exception {
            requestTo("/api/cabinet");
            givenAuthenticatedSession(CUSTOMER_MANAGER);

            filter.doFilter(request, response, chain);

            then(authService).should().getCurrentUser(request, response, sessionStore);
            then(authService).should(never()).tryToRememberMeLogin(any(), any(), any());
        }
    }

    // -- 3. authorization -----------------------------------------------------

    @Nested
    @DisplayName("authorization")
    class AuthorizationTests {

        @ParameterizedTest(name = "{0} is accessible to {1}")
        @CsvSource({
                "/api/admin,                    ADMIN",
                "/api/admin/users,              ADMIN",
                "/api/dashboard/admin,          ADMIN",
                "/api/dashboard/flight-manager, ADMIN",
                "/api/dashboard/flight-manager, FLIGHT_MANAGER",
                "/api/dashboard/customer-manager, ADMIN",
                "/api/dashboard/customer-manager, FLIGHT_MANAGER",
                "/api/dashboard/customer-manager, CUSTOMER_MANAGER",
                "/api/cabinet,                  ADMIN",
                "/api/cabinet,                  FLIGHT_MANAGER",
                "/api/cabinet,                  CUSTOMER_MANAGER"
        })
        @DisplayName("role/path combinations that must be allowed")
        void allowedRolePathCombinations(String path, Role role) throws Exception {
            requestTo(path);
            givenAuthenticatedSession(userWithRole(role));

            filter.doFilter(request, response, chain);

            then(chain).should().doFilter(request, response);
            then(response).should(never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
        }

        @ParameterizedTest(name = "{0} is forbidden to {1}")
        @CsvSource({
                "/api/admin,                    FLIGHT_MANAGER",
                "/api/admin,                    CUSTOMER_MANAGER",
                "/api/dashboard/admin,          FLIGHT_MANAGER",
                "/api/dashboard/admin,          CUSTOMER_MANAGER",
                "/api/dashboard/flight-manager, CUSTOMER_MANAGER"
        })
        @DisplayName("role/path combinations that must be forbidden")
        void forbiddenRolePathCombinations(String path, Role role) throws Exception {
            requestTo(path);
            givenAuthenticatedSession(userWithRole(role));
            given(response.getWriter()).willReturn(writer);

            filter.doFilter(request, response, chain);

            then(response).should().setStatus(HttpServletResponse.SC_FORBIDDEN);
            then(writer).should().write(bodyCaptor.capture());
            assertThat(bodyCaptor.getValue())
                    .contains("FORBIDDEN")
                    .contains("permission");
            then(chain).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("paths with no role restriction (logout, cabinet) allow any authenticated user")
        void unrestrictedPathAllowsAnyAuthenticatedRole() throws Exception {
            requestTo("/api/auth/logout");
            givenAuthenticatedSession(CUSTOMER_MANAGER);

            filter.doFilter(request, response, chain);

            then(chain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("default-deny: an unknown path is forbidden even for authenticated users")
        void unknownPathIsForbiddenForAuthenticatedUser() throws Exception {
            //simulates a servlet added without updating ENDPOINT_ROLES
            requestTo("/api/statistics");
            givenAuthenticatedSession(CUSTOMER_MANAGER);
            given(response.getWriter()).willReturn(writer);

            filter.doFilter(request, response, chain);

            then(response).should().setStatus(HttpServletResponse.SC_FORBIDDEN);
            then(chain).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("word-boundary: '/api/adminEvil' is not matched by the '/api/admin' prefix")
        void prefixDoesNotSwallowLongerPathSharingCharacters() throws Exception {
            requestTo("/api/adminEvil");
            givenAuthenticatedSession(ADMIN);
            given(response.getWriter()).willReturn(writer);

            filter.doFilter(request, response, chain);

            //unknown path → denied despite ADMIN role; proves segment-boundary matching
            then(response).should().setStatus(HttpServletResponse.SC_FORBIDDEN);
            then(chain).shouldHaveNoInteractions();
        }
    }

    // -- 4. response headers ---------------------------------------------------

    @Nested
    @DisplayName("security headers")
    class SecurityHeaderTests {

        @Test
        @DisplayName("all seven security headers are set on every response")
        void setsAllSecurityHeaders() throws Exception {
            requestTo("/api/health");

            filter.doFilter(request, response, chain);

            then(response).should().setHeader("X-Content-Type-Options", "nosniff");
            then(response).should().setHeader("X-Frame-Options", "DENY");
            then(response).should().setHeader("X-XSS-Protection", "1; mode=block");
            then(response).should().setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            then(response).should().setHeader("Pragma", "no-cache");
            then(response).should().setHeader("Content-Security-Policy", "default-src 'self'");
            then(response).should().setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        }

        @Test
        @DisplayName("security headers are set even when the request is rejected")
        void headersSetOnRejectedRequests() throws Exception {
            requestTo("/api/cabinet");
            givenAnonymous();

            filter.doFilter(request, response, chain);

            then(response).should().setHeader("X-Content-Type-Options", "nosniff");
            then(response).should().setHeader("X-Frame-Options", "DENY");
        }
    }

    // -- 5. request URI handling ------------------------------------------------

    @Nested
    @DisplayName("path resolution")
    class PathResolutionTests {

        @Test
        @DisplayName("context path is stripped before matching public endpoints")
        void contextPathIsStrippedBeforeMatching() throws Exception {
            given(request.getContextPath()).willReturn("/airline");
            given(request.getRequestURI()).willReturn("/airline/api/auth/login");

            filter.doFilter(request, response, chain);

            then(chain).should().doFilter(request, response);
            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("URI without the context prefix still matches")
        void uriWithoutContextPathMatches() throws Exception {
            given(request.getContextPath()).willReturn("/airline");
            given(request.getRequestURI()).willReturn("/api/health");

            filter.doFilter(request, response, chain);

            then(chain).should().doFilter(request, response);
        }
    }
}
