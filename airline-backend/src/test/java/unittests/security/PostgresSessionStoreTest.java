package unittests.security;

import com.airline.airlinebackend.dao.SessionDAO;
import com.airline.airlinebackend.model.Session;
import com.airline.airlinebackend.security.PostgresSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.jee.context.JEEContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PostgresSessionStore}.
 * <p>
 * Uses a real {@link JEEContext} wrapping mocked {@link HttpServletRequest}/{@link HttpServletResponse}
 * instances, and a mocked {@link SessionDAO} injected via the store's package-visible (made public for
 * testing) constructor. The store's static thread-local session cache is cleared after every test to
 * avoid state leaking across test methods executed on the same thread.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostgresSessionStore")
public class PostgresSessionStoreTest {
    private static final String COOKIE_NAME = "AIRLINE_SESSION";

    @Mock
    private SessionDAO sessionDao;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private PostgresSessionStore store;
    private JEEContext context;

    @BeforeEach
    void setUp() {
        store = new PostgresSessionStore(sessionDao);
        context = new JEEContext(request,response);
    }

    @AfterEach
    void tearDown() {
        PostgresSessionStore.clearCurrentSession();
    }

    private Session buildSession(String id, boolean expired) {
        Instant expiresAt = expired
                ? Instant.now().minus(5,ChronoUnit.MINUTES)
                : Instant.now().plus(30,ChronoUnit.MINUTES);
        return new Session(id, null, "JUNIT-Agent", "127.0.0.1", new HashMap<>(), expiresAt);
    }

    @Nested
    @DisplayName("getSessionId()")
    class GetSessionIdTest {
        @Test
        @DisplayName("should return empty when no cookie present and createSession is false")
        void getSessionId_noCookieAndNoCreate_returnsEmpty() {
            // Arrange
            given(request.getCookies()).willReturn(null);

            // Act
            Optional<String> result = store.getSessionId(context, false);

            // Assert
            assertThat(result).isEmpty();
            then(sessionDao).should(never()).save(any());
        }

        @Test
        @DisplayName("should create a new session and set cookie when no cookie present and createSession true")
        void getSessionId_noCookieAndCreate_returnsNewSession() {
            given(request.getCookies()).willReturn(null);

            Optional<String> result = store.getSessionId(context, true);

            assertThat(result).isPresent();
            then(sessionDao).should().save(any(Session.class));

            ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
            verify(response).addHeader(eq("Set-Cookie"), headerCaptor.capture());
            assertThat(headerCaptor.getValue())
                    .startsWith(COOKIE_NAME + "=" + result.get())
                    .contains("HttpOnly")
                    .contains("Secure")
                    .contains("SameSite=Strict");
        }

        @Test
        @DisplayName("should delete cookie and return empty when cookie references an expired session")
        void getSessionId_expiredCookie_deletesCookieAndReturnsEmpty() {
            // Arrange
            String sessionId = "expired-session-id";
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(COOKIE_NAME, sessionId);
            given(request.getCookies()).willReturn(new jakarta.servlet.http.Cookie[]{cookie});
            given(sessionDao.findById(sessionId)).willReturn(Optional.of(buildSession(sessionId, true)));

            // Act
            Optional<String> result = store.getSessionId(context, false);

            // Assert
            assertThat(result).isEmpty();
            ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
            verify(response).addHeader(eq("Set-Cookie"), headerCaptor.capture());
            assertThat(headerCaptor.getValue()).startsWith(COOKIE_NAME + "=").contains("Max-Age=0");
        }

        @Test
        @DisplayName("should delete cookie and create new session when cookie session is expired and createSession is true")
        void getSessionId_expiredCookieAndCreate_deletesThenCreatesNewSession() {
            // Arrange
            String sessionId = "expired-session-id";
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(COOKIE_NAME, sessionId);
            given(request.getCookies()).willReturn(new jakarta.servlet.http.Cookie[]{cookie});
            given(sessionDao.findById(sessionId)).willReturn(Optional.of(buildSession(sessionId, true)));

            // Act
            Optional<String> result = store.getSessionId(context, true);

            // Assert
            assertThat(result).isPresent().hasValueSatisfying(id -> assertThat(id).isNotEqualTo(sessionId));
            then(sessionDao).should().save(any(Session.class));
            // Two Set-Cookie headers expected: one deleting the old cookie, one setting the new one
            verify(response, org.mockito.Mockito.times(2)).addHeader(eq("Set-Cookie"), anyString());
        }

        @Test
        @DisplayName("should delete cookie and return empty when session referenced by cookie no longer exists")
        void getSessionId_cookieWithoutMatchingSession_deletesCookieAndReturnsEmpty() {
            // Arrange
            String sessionId = "missing-session-id";
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(COOKIE_NAME, sessionId);
            given(request.getCookies()).willReturn(new jakarta.servlet.http.Cookie[]{cookie});
            given(sessionDao.findById(sessionId)).willReturn(Optional.empty());

            // Act
            Optional<String> result = store.getSessionId(context, false);

            // Assert
            assertThat(result).isEmpty();
            then(response).should().addHeader(eq("Set-Cookie"), anyString());
        }
    }

    @Nested
    @DisplayName("get()")
    class GetTest {
        @Test
        @DisplayName("should return empty when there is no current session")
        void get_noCurrentSession_returnsEmpty() {
            // Arrange
            given(request.getCookies()).willReturn(null);

            // Act
            Optional<Object> result = store.get(context, "key");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return a stored value when key exists in session data")
        void returnValue_inSessionData() {
            String sessionId = "session-id";
            given(request.getCookies()).willReturn(new jakarta.servlet.http.Cookie[]{
                    new jakarta.servlet.http.Cookie(COOKIE_NAME, sessionId)
            });
            Session session = buildSession(sessionId, false);
            session.getSessionData().put("key", "value");
            given(sessionDao.findById(sessionId)).willReturn(Optional.of(session));

            Optional<Object> result = store.get(context, "key");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("value");
        }

        @Test
        @DisplayName("should return empty when key does not exist in session data")
        void get_keyMissing_returnsEmpty() {
            // Arrange
            String sessionId = "session-without-key";
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(COOKIE_NAME, sessionId);
            given(request.getCookies()).willReturn(new jakarta.servlet.http.Cookie[]{cookie});
            given(sessionDao.findById(sessionId)).willReturn(Optional.of(buildSession(sessionId, false)));

            // Act
            Optional<Object> result = store.get(context, "missing-key");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("set()")
    class SetTest {
        @Test
        @DisplayName("should create a session on demand and persist the new key/value pair")
        void setNoExistingSession_PersistNewKeyValuePair() {
            given(request.getCookies()).willReturn(null);

            store.set(context, "foo", "ass");

            ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
            then(sessionDao).should().save(sessionCaptor.capture());

            assertThat(sessionCaptor.getValue()).isNotNull()
                    .extracting(Session::getSessionData)
                    .satisfies(sessionData ->
                            assertThat(sessionData).containsEntry("foo", "ass"));
        }

        @Test
        @DisplayName("should remove the key from session data when the value is null")
        void setNullValue_removeKeyFromSessionData() {
            String sessionId = "session-to-mutate";
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(COOKIE_NAME, sessionId);
            given(request.getCookies()).willReturn(new jakarta.servlet.http.Cookie[]{cookie});
            Session session = buildSession(sessionId, false);
            session.getSessionData().put("foo", "bar");
            given(sessionDao.findById(sessionId)).willReturn(Optional.of(session));

            // Act
            store.set(context, "foo", null);

            // Assert
            ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
            then(sessionDao).should().update(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getSessionData()).doesNotContainKey("foo");
        }
    }

    @Nested
    @DisplayName("destroySession()")
    class DestroySessionTest {
        @Test
        @DisplayName("should delete session from DAO and clear cookie when a session exists")
        void destroySession_existingSession_deletesFromDaoAndClearsCookie() {
            // Arrange
            String sessionId = "session-to-destroy";
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(COOKIE_NAME, sessionId);
            given(request.getCookies()).willReturn(new jakarta.servlet.http.Cookie[]{cookie});
            given(sessionDao.findById(sessionId)).willReturn(Optional.of(buildSession(sessionId, false)));

            // Act
            boolean result = store.destroySession(context);

            // Assert
            assertThat(result).isTrue();
            then(sessionDao).should().delete(sessionId);
            then(response).should().addHeader(eq("Set-Cookie"), anyString());
        }

        @Test
        @DisplayName("should still clear cookie and return true when there is no current session")
        void destroySession_noCurrentSession_returnsTrueWithoutDaoDelete() {
            // Arrange
            given(request.getCookies()).willReturn(null);

            // Act
            boolean result = store.destroySession(context);

            // Assert
            assertThat(result).isTrue();
            then(sessionDao).should(never()).delete(anyString());
            then(response).should().addHeader(eq("Set-Cookie"), anyString());
        }
    }

    @Nested
    @DisplayName("getTrackableSession() / buildFromTrackableSession()")
    class TrackableSessionTests {

        @Test
        @DisplayName("getTrackableSession should return the session id without creating a new session")
        void getTrackableSession_noCookie_returnsEmptyWithoutCreating() {
            // Arrange
            given(request.getCookies()).willReturn(null);

            // Act
            Optional<Object> result = store.getTrackableSession(context);

            // Assert
            assertThat(result).isEmpty();
            then(sessionDao).should(never()).save(any());
        }

        @Test
        @DisplayName("buildFromTrackableSession should rebuild the store when the session id is valid")
        void buildFromTrackableSession_validSessionId_returnsPopulatedStore() {
            // Arrange
            String sessionId = "trackable-session-id";
            given(sessionDao.findById(sessionId)).willReturn(Optional.of(buildSession(sessionId, false)));

            // Act
            Optional<SessionStore> result = store.buildFromTrackableSession(context, sessionId);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(store);
        }

        @Test
        @DisplayName("buildFromTrackableSession should return empty when the session is expired")
        void buildFromTrackableSession_expiredSession_returnsEmpty() {
            // Arrange
            String sessionId = "expired-trackable-id";
            given(sessionDao.findById(sessionId)).willReturn(Optional.of(buildSession(sessionId, true)));

            // Act
            Optional<SessionStore> result = store.buildFromTrackableSession(context, sessionId);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("buildFromTrackableSession should return empty when the trackable object is not a String")
        void buildFromTrackableSession_nonStringTrackable_returnsEmpty() {
            // Act
            Optional<SessionStore> result = store.buildFromTrackableSession(context, 12345);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // renewSession()
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("renewSession()")
    class RenewSessionTests {

        @Test
        @DisplayName("should delete old session, create a new one, and preserve data and user id")
        void renewSession_existingSession_rotatesIdAndPreservesData() {
            // Arrange
            String oldSessionId = "old-session-id";
            UUID userId = UUID.randomUUID();
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(COOKIE_NAME, oldSessionId);
            given(request.getCookies()).willReturn(new jakarta.servlet.http.Cookie[]{cookie});

            Session oldSession = buildSession(oldSessionId, false);
            oldSession.setUserId(userId);
            oldSession.getSessionData().put("foo", "bar");
            given(sessionDao.findById(oldSessionId)).willReturn(Optional.of(oldSession));

            // Stub findById for the newly generated session id: return whatever was passed to save()
            ArgumentCaptor<Session> savedSessionCaptor = ArgumentCaptor.forClass(Session.class);
            lenient().doAnswer(invocation -> {
                Session saved = invocation.getArgument(0);
                given(sessionDao.findById(saved.getId())).willReturn(Optional.of(saved));
                return saved;
            }).when(sessionDao).save(savedSessionCaptor.capture());

            // Act
            boolean result = store.renewSession(context);

            // Assert
            assertThat(result).isTrue();
            then(sessionDao).should().delete(oldSessionId);
            then(sessionDao).should().save(any(Session.class));

            ArgumentCaptor<Session> updatedSessionCaptor = ArgumentCaptor.forClass(Session.class);
            then(sessionDao).should().update(updatedSessionCaptor.capture());
            Session updatedSession = updatedSessionCaptor.getValue();
            assertThat(updatedSession.getId()).isNotEqualTo(oldSessionId);
            assertThat(updatedSession.getUserId()).isEqualTo(userId);
            assertThat(updatedSession.getSessionData()).containsEntry("foo", "bar");
        }

        @Test
        @DisplayName("should create a fresh session when there is no existing session to renew")
        void renewSession_noExistingSession_createsFreshSession() {
            // Arrange
            given(request.getCookies()).willReturn(null);

            lenient().doAnswer(invocation -> {
                Session saved = invocation.getArgument(0);
                given(sessionDao.findById(saved.getId())).willReturn(Optional.of(saved));
                return saved;
            }).when(sessionDao).save(any(Session.class));

            // Act
            boolean result = store.renewSession(context);

            // Assert
            assertThat(result).isTrue();
            then(sessionDao).should(never()).delete(anyString());
            then(sessionDao).should().save(any(Session.class));
        }
    }

    // -------------------------------------------------------------------------
    // setUserId() / getUserId()
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("setUserId() / getUserId()")
    class UserIdTests {

        @Test
        @DisplayName("setUserId should associate the user id with the current session and persist it")
        void setUserId_withExistingSession_persistsUserId() {
            // Arrange
            String sessionId = "session-for-user";
            UUID userId = UUID.randomUUID();
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(COOKIE_NAME, sessionId);
            given(request.getCookies()).willReturn(new jakarta.servlet.http.Cookie[]{cookie});
            given(sessionDao.findById(sessionId)).willReturn(Optional.of(buildSession(sessionId, false)));

            // Act
            store.setUserId(context, userId);

            // Assert
            ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
            then(sessionDao).should().update(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("getUserId should return the user id when present in the current session")
        void getUserId_sessionWithUserId_returnsUserId() {
            // Arrange
            String sessionId = "session-with-user";
            UUID userId = UUID.randomUUID();
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(COOKIE_NAME, sessionId);
            given(request.getCookies()).willReturn(new jakarta.servlet.http.Cookie[]{cookie});
            Session session = buildSession(sessionId, false);
            session.setUserId(userId);
            given(sessionDao.findById(sessionId)).willReturn(Optional.of(session));

            // Act
            Optional<UUID> result = store.getUserId(context);

            // Assert
            assertThat(result).contains(userId);
        }

        @Test
        @DisplayName("getUserId should return empty when there is no current session")
        void getUserId_noCurrentSession_returnsEmpty() {
            // Arrange
            given(request.getCookies()).willReturn(null);

            // Act
            Optional<UUID> result = store.getUserId(context);

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
