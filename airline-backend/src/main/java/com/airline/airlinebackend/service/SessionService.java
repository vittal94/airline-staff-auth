package com.airline.airlinebackend.service;

import com.airline.airlinebackend.dao.EmailConfirmationTokenDAO;
import com.airline.airlinebackend.dao.LoginAttemptDAO;
import com.airline.airlinebackend.dao.RememberMeTokenDAO;
import com.airline.airlinebackend.dao.SessionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service for session clean and maintenance
 */
public class SessionService {
    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    private final SessionDAO sessionDAO;
    private final RememberMeTokenDAO rememberMeTokenDAO;
    private final LoginAttemptDAO loginAttemptDAO;
    private final EmailConfirmationTokenDAO emailConfirmationTokenDAO;

    public SessionService() {
        this(
                new SessionDAO(),
                new RememberMeTokenDAO(),
                new LoginAttemptDAO(),
                new EmailConfirmationTokenDAO()
        );
    }

    public SessionService(
            SessionDAO sessionDAO,
            RememberMeTokenDAO rememberMeTokenDAO,
            LoginAttemptDAO loginAttemptDAO,
            EmailConfirmationTokenDAO emailConfirmationTokenDAO
            ) {
        this.sessionDAO = sessionDAO;
        this.rememberMeTokenDAO = rememberMeTokenDAO;
        this.loginAttemptDAO = loginAttemptDAO;
        this.emailConfirmationTokenDAO = emailConfirmationTokenDAO;
    }

    /**
     * Cleans up all expired tokens and sessions
     */
    public CleanupResult cleanupExpired() {
        CleanupResult result = new CleanupResult();

        result.sessionsDeleted = sessionDAO.deleteExpired();
        result.rememberMeTokensDeleted = rememberMeTokenDAO.deleteExpired();
        result.emailTokensDeleted = emailConfirmationTokenDAO.deleteExpired();

        //Delete login attempts older than 24 hours
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        result.loginAttemptsDeleted = loginAttemptDAO.deleteOlderThen(cutoff);

        logger.info("Cleanup completed: {} sessions, {} remember-me tokens, " +
                        "{} email tokens, {} login attempts deleted",
                result.sessionsDeleted, result.rememberMeTokensDeleted,
                result.emailTokensDeleted, result.loginAttemptsDeleted);

        return result;
    }

    public static class CleanupResult {
        public int sessionsDeleted;
        public int rememberMeTokensDeleted;
        public int emailTokensDeleted;
        public int loginAttemptsDeleted;
    }
}
