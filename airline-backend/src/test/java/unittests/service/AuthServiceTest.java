package unittests.service;

import com.airline.airlinebackend.dao.SessionDAO;
import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.dto.request.LoginRequest;
import com.airline.airlinebackend.exception.AccountBlockedException;
import com.airline.airlinebackend.exception.AccountNotApprovedException;
import com.airline.airlinebackend.exception.AuthenticationException;
import com.airline.airlinebackend.exception.EmailNotConfirmedException;
import com.airline.airlinebackend.exception.RateLimitExceededException;
import com.airline.airlinebackend.exception.ValidationException;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.enums.Role;
import com.airline.airlinebackend.model.enums.UserStatus;
import com.airline.airlinebackend.security.PasswordEncoder;
import com.airline.airlinebackend.security.PostgresSessionStore;
import com.airline.airlinebackend.security.RememberMeService;
import com.airline.airlinebackend.service.AuthService;
import com.airline.airlinebackend.service.RateLimitService;
import com.airline.airlinebackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pac4j.core.context.WebContext;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link AuthService}.
 */
@DisplayName("AuthService")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_EMAIL = "  Test@Mail.COM  ";
    private static final String EMAIL = "test@mail.com";
    private static final String PASSWORD = "CorrectPassword123.";
    private static final String IP = "127.0.0.1";
    private static final UUID USER_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    // Argon2id hashing is expensive (~64 MB per call) — compute once, share read-only across tests
    private static final String PASSWORD_HASH = PasswordEncoder.encode(PASSWORD);

    @Mock
    private UserDAO userDAO;
    @Mock
    private SessionDAO sessionDAO;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private RememberMeService rememberMeService;
    @Mock
    private UserService userService;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private HttpServletResponse httpResponse;
    @Mock
    private PostgresSessionStore sessionStore;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // ServletUtil.getClientIP() reads X-Forwarded-For first, then X-Real-IP.
        // Only login() resolves the client IP — lenient so non-login tests don't trip strict stubs
        lenient().doReturn(null).when(httpRequest).getHeader("X-Forwarded-For");
        lenient().doReturn(IP).when(httpRequest).getHeader("X-Real-IP");
    }

    private LoginRequest loginRequest(String email, String password) {
        // LoginRequest constructor parameter order is (password, email)
        return new LoginRequest(password, email);
    }

    private User userWithStatus(UserStatus status) {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .role(Role.FLIGHT_MANAGER)
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("should throw ValidationException for malformed email and never touch rate limiter and DAO")
        void login_invalidEmailFormat_throwsValidationExceptionWithoutSideEffects() {
            // Arrange
            LoginRequest request = loginRequest("wrong-format-email", PASSWORD);

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Email is not valid");

            // a malformed request must not burn a rate-limit slot nor hit the DB
            then(rateLimitService).shouldHaveNoInteractions();
            then(userDAO).shouldHaveNoInteractions();
            then(sessionStore).shouldHaveNoInteractions();
            then(rememberMeService).shouldHaveNoInteractions();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("should throw ValidationException for null/blank email and never touch rate limiter and DAO")
        void login_blankOrNullEmail_throwsValidationException(String email) {
            // Arrange
            LoginRequest request = loginRequest(email, PASSWORD);

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Email is required");

            then(rateLimitService).shouldHaveNoInteractions();
            then(userDAO).shouldHaveNoInteractions();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("should throw ValidationException for null/blank password and never touch rate limiter and DAO")
        void login_blankOrNullPassword_throwsValidationExceptionWithoutSideEffects(String password) {
            // Arrange
            LoginRequest request = loginRequest(RAW_EMAIL, password);

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Password is required");

            then(rateLimitService).shouldHaveNoInteractions();
            then(userDAO).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should propagate RateLimitExceededException unwrapped and never touch the DAO")
        void login_rateLimitExceeded_propagatesExceptionWithoutDaoLookup() {
            // Arrange
            LoginRequest request = loginRequest(RAW_EMAIL, PASSWORD);
            willThrow(new RateLimitExceededException("Too many failed login attempts. Please try again later.", 900L))
                    .given(rateLimitService).checkRateLimit(EMAIL, IP);

            // Act & Assert
            // must reach the servlet layer as RateLimitExceededException (not wrapped into
            // AuthenticationException) so it maps to HTTP 429 with the Retry-After header
            assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(RateLimitExceededException.class)
                    .hasMessageContaining("Too many failed login attempts");

            then(userDAO).shouldHaveNoInteractions();
            then(rateLimitService).should(never()).recordFailedAttempt(any(), any());
            then(rateLimitService).should(never()).recordSuccessfulLogin(any(), any());
            then(sessionStore).shouldHaveNoInteractions();
            then(rememberMeService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should throw AuthenticationException and record a failed attempt when email is unknown")
        void login_unknownEmail_throwsAuthenticationExceptionAndRecordsFailedAttempt() {
            // Arrange
            LoginRequest request = loginRequest(RAW_EMAIL, PASSWORD);
            given(userDAO.findByEmail(EMAIL)).willReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("Invalid email or password");

            then(rateLimitService).should().recordFailedAttempt(EMAIL, IP);
            then(rateLimitService).should(never()).recordSuccessfulLogin(any(), any());
            then(sessionStore).shouldHaveNoInteractions();
            then(rememberMeService).shouldHaveNoInteractions();
            then(userService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should throw AuthenticationException and record a failed attempt when password is wrong")
        void login_wrongPassword_throwsAuthenticationExceptionAndRecordsFailedAttempt() {
            // Arrange
            LoginRequest request = loginRequest(RAW_EMAIL, "WrongPassword123.");
            given(userDAO.findByEmail(EMAIL)).willReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("Invalid email or password");

            then(rateLimitService).should().recordFailedAttempt(EMAIL, IP);
            then(rateLimitService).should(never()).recordSuccessfulLogin(any(), any());
            then(sessionStore).shouldHaveNoInteractions();
            then(rememberMeService).shouldHaveNoInteractions();
            then(userService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should throw AuthenticationException when stored password hash is null " +
                "(corrupted data must not leak a 500)")
        void login_nullPasswordHash_throwsAuthenticationExceptionAndRecordsFailedAttempt() {
            // Arrange
            LoginRequest request = loginRequest(RAW_EMAIL, PASSWORD);
            User user = User.builder()
                    .id(USER_ID)
                    .email(EMAIL)
                    .passwordHash(null)
                    .status(UserStatus.ACTIVE)
                    .build();
            given(userDAO.findByEmail(EMAIL)).willReturn(Optional.of(user));

            // Act & Assert
            // PasswordEncoder.verify catches the NPE from split(null) and returns false,
            // so a corrupted row degrades to "invalid credentials" instead of an error page
            assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("Invalid email or password");

            then(rateLimitService).should().recordFailedAttempt(EMAIL, IP);
            then(sessionStore).shouldHaveNoInteractions();
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("should throw the status-specific exception and create no session when status is not ACTIVE")
        void login_nonActiveStatus_throwsStatusSpecificExceptionWithoutSession(UserStatus status) {
            // Arrange
            LoginRequest request = loginRequest(RAW_EMAIL, PASSWORD);
            given(userDAO.findByEmail(EMAIL)).willReturn(Optional.of(userWithStatus(status)));

            // Act & Assert
            // password is correct — the only reason for rejection is the account status
            assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(expectedExceptionFor(status));

            // a correct password with a non-ACTIVE status must count neither as failure nor
            // success, and must never create a session or remember-me token
            then(rateLimitService).should(never()).recordFailedAttempt(any(), any());
            then(rateLimitService).should(never()).recordSuccessfulLogin(any(), any());
            then(sessionStore).shouldHaveNoInteractions();
            then(rememberMeService).shouldHaveNoInteractions();
            then(userService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should log in an ACTIVE user: lookup by sanitized email, verify raw password, " +
                "record success, rotate session id, create remember-me token and update last login")
        void login_validCredentials_returnsUserAndPerformsAllSideEffectsInOrder() {
            // Arrange
            LoginRequest request = loginRequest(RAW_EMAIL, PASSWORD);
            User user = userWithStatus(UserStatus.ACTIVE);
            // findByEmail is stubbed with the SANITIZED email — proves the lookup uses
            // the sanitized value ("  Test@Mail.COM  " -> "test@mail.com")
            given(userDAO.findByEmail(EMAIL)).willReturn(Optional.of(user));

            // Act
            User result = authService.login(request, httpRequest, httpResponse, sessionStore);

            // Assert
            assertThat(result).isSameAs(user);

            InOrder inOrder = inOrder(rateLimitService, userDAO, sessionStore, rememberMeService, userService);
            then(rateLimitService).should(inOrder).checkRateLimit(EMAIL, IP);
            then(userDAO).should(inOrder).findByEmail(EMAIL);
            then(rateLimitService).should(inOrder).recordSuccessfulLogin(EMAIL, IP);
            // session id rotation is the session-fixation defense — must always happen on success
            then(sessionStore).should(inOrder).renewSessionForUser(any(WebContext.class), eq(USER_ID));
            then(rememberMeService).should(inOrder).createRememberMeToken(httpRequest, httpResponse, USER_ID);
            then(userService).should(inOrder).updateLastLogin(USER_ID);

            then(rateLimitService).should(never()).recordFailedAttempt(any(), any());
        }

        @Test
        @DisplayName("should wrap unexpected DAO errors in AuthenticationException and not count them " +
                "as failed attempts")
        void login_daoFailure_wrapsInAuthenticationExceptionWithoutRecordingFailure() {
            // Arrange
            LoginRequest request = loginRequest(RAW_EMAIL, PASSWORD);
            given(userDAO.findByEmail(EMAIL)).willThrow(new RuntimeException("connection refused"));

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("Login failed")
                    .hasRootCauseMessage("connection refused");

            // an infrastructure failure must not count against the user's rate limit
            then(rateLimitService).should(never()).recordFailedAttempt(any(), any());
            then(rateLimitService).should(never()).recordSuccessfulLogin(any(), any());
            then(sessionStore).shouldHaveNoInteractions();
            then(rememberMeService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should fail the whole login when remember-me token creation fails " +
                "after session rotation")
        void login_rememberMeCreationFails_failsLoginAfterSessionRotation() {
            // Arrange
            LoginRequest request = loginRequest(RAW_EMAIL, PASSWORD);
            given(userDAO.findByEmail(EMAIL)).willReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));
            willThrow(new RuntimeException("token store down"))
                    .given(rememberMeService).createRememberMeToken(any(), any(), any());

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("Login failed");

            // Documents CURRENT behavior: the session id is already rotated when the token
            // creation fails, so the client holds a fresh session but no authenticated user,
            // and last login is never updated. If remember-me should be best-effort, catch
            // the failure at the call site in AuthService and update this test.
            then(sessionStore).should().renewSessionForUser(any(WebContext.class), eq(USER_ID));
            then(userService).should(never()).updateLastLogin(any());
        }

        private Class<? extends RuntimeException> expectedExceptionFor(UserStatus status) {
            return switch (status) {
                case PENDING_EMAIL_CONFIRMATION -> EmailNotConfirmedException.class;
                case PENDING_APPROVAL -> AccountNotApprovedException.class;
                case BLOCKED -> AccountBlockedException.class;
                default -> throw new IllegalArgumentException("No exception expected for " + status);
            };
        }
    }

    @Nested
    @DisplayName("tryToRememberMeLogin()")
    class TryToRememberMeTests {

        @Test
        @DisplayName("should return empty when no valid remember-me token is present and create no session")
        void tryToRememberMeLogin_noValidToken_returnsEmptyWithoutSession() {
            // Arrange
            given(rememberMeService.validateAndRotate(httpRequest, httpResponse))
                    .willReturn(Optional.empty());

            // Act
            Optional<User> result = authService.tryToRememberMeLogin(httpRequest, httpResponse, sessionStore);

            // Assert
            assertThat(result).isEmpty();
            then(sessionStore).shouldHaveNoInteractions();
            then(userService).shouldHaveNoInteractions();
            // a missing/expired token must not clear the cookie again — validateAndRotate
            // already handles invalid cookies internally
            then(rememberMeService).should(never()).clearRememberMe(any(), any());
        }

        @Test
        @DisplayName("should log in an ACTIVE user: create session, update last login and return the user")
        void tryToRememberMeLogin_activeUser_createsSessionAndUpdatesLastLogin() {
            // Arrange
            User user = userWithStatus(UserStatus.ACTIVE);
            given(rememberMeService.validateAndRotate(httpRequest, httpResponse))
                    .willReturn(Optional.of(user));

            // Act
            Optional<User> result = authService.tryToRememberMeLogin(httpRequest, httpResponse, sessionStore);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(user);

            InOrder inOrder = inOrder(sessionStore, userService);
            then(sessionStore).should(inOrder).renewSessionForUser(any(WebContext.class), eq(USER_ID));
            then(userService).should(inOrder).updateLastLogin(USER_ID);

            then(rememberMeService).should(never()).clearRememberMe(any(), any());
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("should clear the token and return empty when the account is no longer ACTIVE")
        void tryToRememberMeLogin_nonActiveStatus_clearsTokenAndReturnsEmpty(UserStatus status) {
            // Arrange
            given(rememberMeService.validateAndRotate(httpRequest, httpResponse))
                    .willReturn(Optional.of(userWithStatus(status)));

            // Act
            Optional<User> result = authService.tryToRememberMeLogin(httpRequest, httpResponse, sessionStore);

            // Assert
            // the token was valid, but the account got blocked/deactivated since it was issued —
            // the user must NOT be returned and the stale token must be wiped
            assertThat(result).isEmpty();
            then(rememberMeService).should().clearRememberMe(httpRequest, httpResponse);
            then(sessionStore).shouldHaveNoInteractions();
            then(userService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should propagate errors from token validation without creating a session")
        void tryToRememberMeLogin_tokenStoreFailure_propagatesWithoutSession() {
            // Arrange
            given(rememberMeService.validateAndRotate(httpRequest, httpResponse))
                    .willThrow(new RuntimeException("token store down"));

            // Act & Assert
            // tryToRememberMeLogin has no catch-all — infra failures propagate to the caller
            assertThatThrownBy(() -> authService.tryToRememberMeLogin(httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("token store down");

            then(sessionStore).shouldHaveNoInteractions();
            then(userService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("should clear remember-me cookie and destroy session of a logged-in user")
        void logout_userInSession_clearsTokenAndDestroysSession() {
            // Act
            authService.logout(httpRequest, httpResponse, sessionStore);

            // Assert
            InOrder inOrder = inOrder(sessionStore, rememberMeService);
            // userId is read BEFORE the session is destroyed — after destroySession it is gone
            then(sessionStore).should(inOrder).getUserId(any(WebContext.class));
            then(rememberMeService).should(inOrder).clearRememberMe(httpRequest, httpResponse);
            then(sessionStore).should(inOrder).destroySession(any(WebContext.class));
        }

        @Test
        @DisplayName("should still clear token and destroy session when no user is bound to the session")
        void logout_anonymousSession_stillCleansUp() {
            // Arrange
            given(sessionStore.getUserId(any(WebContext.class))).willReturn(Optional.empty());

            // Act
            authService.logout(httpRequest, httpResponse, sessionStore);

            // Assert
            // logout must be safe to call for an already-anonymous session (idempotent)
            then(rememberMeService).should().clearRememberMe(httpRequest, httpResponse);
            then(sessionStore).should().destroySession(any(WebContext.class));
        }
    }

    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("should change password, revoke all tokens and sessions, rotate current session " +
                "and issue a fresh remember-me token")
        void changePassword_validCurrentPassword_revokesAllSessionsAndTokensAndRotatesCurrentSession() {
            // Arrange
            given(sessionStore.getSessionId(any(WebContext.class), eq(false)))
                    .willReturn(Optional.of("current-session-id"));

            // Act
            authService.changePassword(USER_ID, PASSWORD, "NewPassword123.", httpRequest, httpResponse, sessionStore);

            // Assert
            InOrder inOrder = inOrder(userService, rememberMeService, sessionDAO, sessionStore);
            then(userService).should(inOrder).changePassword(USER_ID, PASSWORD, "NewPassword123.");
            // password change must invalidate every remember-me token for this user
            then(rememberMeService).should(inOrder).clearAllUserTokens(USER_ID);
            // ...and every session, since the old password must not work anywhere else
            then(sessionDAO).should(inOrder).deleteByUserId(USER_ID);
            // the current session id is rotated so the user stays logged in here
            then(sessionStore).should(inOrder).renewSessionForUser(any(WebContext.class), eq(USER_ID));
            then(rememberMeService).should(inOrder).createRememberMeToken(httpRequest, httpResponse, USER_ID);
        }

        @Test
        @DisplayName("should propagate the error and revoke nothing when current password is wrong")
        void changePassword_wrongCurrentPassword_revokesNothing() {
            // Arrange
            willThrow(new AuthenticationException("Invalid email or password"))
                    .given(userService).changePassword(eq(USER_ID), eq("WrongPassword123."), any());

            // Act & Assert
            assertThatThrownBy(() -> authService.changePassword(
                    USER_ID, "WrongPassword123.", "NewPassword123.", httpRequest, httpResponse, sessionStore))
                    .isInstanceOf(AuthenticationException.class);

            // a FAILED password change must not log the user out everywhere —
            // sessions and tokens survive a rejected change
            then(rememberMeService).should(never()).clearAllUserTokens(any());
            then(sessionDAO).should(never()).deleteByUserId(any());
            then(sessionStore).shouldHaveNoInteractions();
            then(rememberMeService).should(never()).createRememberMeToken(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getCurrentUser()")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return the user bound to the current session")
        void getCurrentUser_userInSession_returnsUser() {
            // Arrange
            User user = userWithStatus(UserStatus.ACTIVE);
            given(sessionStore.getUserId(any(WebContext.class))).willReturn(Optional.of(USER_ID));
            given(userDAO.findById(USER_ID)).willReturn(Optional.of(user));

            // Act
            Optional<User> result = authService.getCurrentUser(httpRequest, httpResponse, sessionStore);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(user);
        }

        @Test
        @DisplayName("should return empty when the session has no bound user (anonymous request)")
        void getCurrentUser_anonymousSession_returnsEmptyWithoutDaoLookup() {
            // Arrange
            given(sessionStore.getUserId(any(WebContext.class))).willReturn(Optional.empty());

            // Act
            Optional<User> result = authService.getCurrentUser(httpRequest, httpResponse, sessionStore);

            // Assert
            assertThat(result).isEmpty();
            // an anonymous session must not trigger a DB lookup
            then(userDAO).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return empty when the session points to a user that no longer exists")
        void getCurrentUser_userDeletedFromDb_returnsEmpty() {
            // Arrange
            // e.g. the user was deleted while their session row was still alive
            given(sessionStore.getUserId(any(WebContext.class))).willReturn(Optional.of(USER_ID));
            given(userDAO.findById(USER_ID)).willReturn(Optional.empty());

            // Act
            Optional<User> result = authService.getCurrentUser(httpRequest, httpResponse, sessionStore);

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
