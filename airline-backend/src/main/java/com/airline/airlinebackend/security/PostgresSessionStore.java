package com.airline.airlinebackend.security;


import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.dao.SessionDAO;
import com.airline.airlinebackend.model.Session;
import com.airline.airlinebackend.util.CookieUtil;
import com.airline.airlinebackend.util.SecureTokenGenerator;
import com.airline.airlinebackend.util.ServletUtil;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.jee.context.JEEContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * PAC4J SessionStore implementation backed by PostgreSQL.
 * Provides stateful session management with sliding expiration.
 */
public class PostgresSessionStore implements SessionStore {

    private static final Logger logger = LoggerFactory.getLogger(PostgresSessionStore.class);

    private final SessionDAO sessionDao;
    private final AppConfig config;

    // Thread-local cache for current request's session
    private static final ThreadLocal<Session> currentSession = new ThreadLocal<>();

    public PostgresSessionStore(SessionDAO sessionDAO) { //for testing usage
        this.sessionDao = sessionDAO;
        this.config = AppConfig.getInstance();
    }

    public PostgresSessionStore() {
        this(new SessionDAO());
    }

    @Override
    public Optional<String> getSessionId(WebContext context, boolean createSession) {
        JEEContext ctx = (JEEContext) context;
        HttpServletRequest request = ctx.getNativeRequest();
        HttpServletResponse response = ctx.getNativeResponse();

        // Try to get session ID from cookie
        Optional<String> sessionId = CookieUtil.getCookieValue(request, config.getSessionCookieName());

        if (sessionId.isPresent()) {
            // Validate session exists and is not expired
            Optional<Session> session = sessionDao.findById(sessionId.get());
            if (session.isPresent() && !session.get().isExpired()) {
                // Update last accessed time (sliding expiration)
                updateSessionExpiration(session.get());
                currentSession.set(session.get());
                return sessionId;
            } else {
                // Invalid or expired session - delete cookie
                CookieUtil.deleteCookie(response, config.getSessionCookieName());
            }
        }

        if (createSession) {
            return Optional.of(createNewSession(request, response));
        }

        return Optional.empty();
    }

    private String createNewSession(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = SecureTokenGenerator.generateSessionId();
        Instant expiresAt = Instant.now().plus(config.getSessionTimeoutMinutes(), ChronoUnit.MINUTES);

        Session session = new Session(
                sessionId,
                null, // User ID set on authentication
                ServletUtil.getClientIP(request),
                ServletUtil.getUserAgent(request),
                new HashMap<>(),
                expiresAt
        );

        sessionDao.save(session);
        currentSession.set(session);

        // Set session cookie
        int maxAge = config.getSessionTimeoutMinutes() * 60;
        CookieUtil.setSecureCookie(response, config.getSessionCookieName(), sessionId, maxAge);

        logger.debug("Created new session: {}", sessionId);
        return sessionId;
    }

    private void updateSessionExpiration(Session session) {
        Instant newExpiry = Instant.now().plus(config.getSessionTimeoutMinutes(), ChronoUnit.MINUTES);
        session.setLastAccessedAt(Instant.now());
        session.setExpiresAt(newExpiry);
        sessionDao.updateLastAccessed(session.getId(), session.getLastAccessedAt(), newExpiry);
    }

    @Override
    public Optional<Object> get(WebContext context, String key) {
        Session session = getCurrentSession(context);
        if (session == null) {
            return Optional.empty();
        }

        Map<String, Object> data = session.getSessionData();
        return Optional.ofNullable(data.get(key));
    }

    @Override
    public void set(WebContext context, String key, Object value) {
        Session session = getOrCreateSession(context);
        if (session != null) {
            if (value == null) {
                session.getSessionData().remove(key);
            } else {
                session.getSessionData().put(key, value);
            }
            sessionDao.update(session);
        }
    }

    @Override
    public boolean destroySession(WebContext context) {
        JEEContext ctx = (JEEContext) context;
        HttpServletResponse response = ctx.getNativeResponse();

        Session session = getCurrentSession(context);
        if (session != null) {
            sessionDao.delete(session.getId());
            currentSession.remove();
        }

        CookieUtil.deleteCookie(response, config.getSessionCookieName());
        logger.debug("Session destroyed");
        return true;
    }

    @Override
    public Optional<Object> getTrackableSession(WebContext context) {
        return getSessionId(context, false).map(id -> id);
    }

    @Override
    public Optional<SessionStore> buildFromTrackableSession(WebContext context, Object trackableSession) {
        if (trackableSession instanceof String sessionId) {
            Optional<Session> session = sessionDao.findById(sessionId);
            if (session.isPresent() && !session.get().isExpired()) {
                currentSession.set(session.get());
                return Optional.of(this);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean renewSession(WebContext context) {
        JEEContext ctx = (JEEContext) context;
        HttpServletRequest request = ctx.getNativeRequest();
        HttpServletResponse response = ctx.getNativeResponse();

        Session oldSession = getCurrentSession(context);
        Map<String, Object> oldData = oldSession != null ?
                new HashMap<>(oldSession.getSessionData()) : new HashMap<>();
        UUID userId = oldSession != null ? oldSession.getUserId() : null;

        // Destroy old session
        if (oldSession != null) {
            sessionDao.delete(oldSession.getId());
        }

        // Create new session with rotated ID
        String newSessionId = createNewSession(request, response);
        Session newSession = sessionDao.findById(newSessionId).orElseThrow();
        newSession.setSessionData(oldData);
        newSession.setUserId(userId);
        sessionDao.update(newSession);
        currentSession.set(newSession);

        logger.debug("Session renewed: {} -> {}",
                oldSession != null ? oldSession.getId() : "null", newSessionId);
        return true;
    }

    private Session getCurrentSession(WebContext context) {
        Session session = currentSession.get();
        if (session == null) {
            getSessionId(context, false);
            session = currentSession.get();
        }
        return session;
    }

    private Session getOrCreateSession(WebContext context) {
        Session session = currentSession.get();
        if (session == null) {
            getSessionId(context, true);
            session = currentSession.get();
        }
        return session;
    }

    /**
     * Associates a user with the current session.
     */
    public void setUserId(WebContext context, UUID userId) {
        Session session = getOrCreateSession(context);
        if (session != null) {
            session.setUserId(userId);
            sessionDao.update(session);
        }
    }

    /**
     * Gets the user ID from the current session.
     */
    public Optional<UUID> getUserId(WebContext context) {
        Session session = getCurrentSession(context);
        return session != null ? Optional.ofNullable(session.getUserId()) : Optional.empty();
    }

    /**
     * Cleans up thread-local after request processing.
     */
    public static void clearCurrentSession() {
        currentSession.remove();
    }
}