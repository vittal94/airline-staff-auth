package com.airline.airlinebackend.service;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.dao.LoginAttemptDAO;
import com.airline.airlinebackend.exception.RateLimitExceededException;
import com.airline.airlinebackend.model.LoginAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service for rate limiting login attempts.
 */
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    private final LoginAttemptDAO loginAttemptDao;
    private final AppConfig config;

    public RateLimitService() {
        this(new LoginAttemptDAO(), AppConfig.getInstance());
    }

    RateLimitService(LoginAttemptDAO loginAttemptDao, AppConfig config) {
        this.loginAttemptDao = loginAttemptDao;
        this.config = config;
    }

    /**
     * Checks if login attempt is allowed based on rate limits.
     */
    public void checkRateLimit(String email, String ipAddress) {
        Instant emailWindowStart = Instant.now()
                .minus(config.getRateLimitLoginWindowMinutes(), ChronoUnit.MINUTES);
        Instant ipWindowStart = Instant.now()
                .minus(config.getRateLimitIpWindowMinutes(), ChronoUnit.MINUTES);

        // Check email-based rate limit
        int emailAttempts = loginAttemptDao.countFailedAttemptsByEmail(email, emailWindowStart);
        if (emailAttempts >= config.getRateLimitLoginMaxAttempts()) {
            long retryAfter = config.getRateLimitLoginWindowMinutes() * 60L;
            logger.warn("Rate limit exceeded for email: {} ({} attempts)", email, emailAttempts);
            throw new RateLimitExceededException(
                    "Too many failed login attempts. Please try again later.", retryAfter);
        }

        // Check IP-based rate limit
        int ipAttempts = loginAttemptDao.countFailedAttemptsByIpAddress(ipAddress, ipWindowStart);
        if (ipAttempts >= config.getRateLimitIpMaxAttempts()) {
            long retryAfter = config.getRateLimitIpWindowMinutes() * 60L;
            logger.warn("Rate limit exceeded for IP: {} ({} attempts)", ipAddress, ipAttempts);
            throw new RateLimitExceededException(
                    "Too many failed login attempts from this IP. Please try again later.", retryAfter);
        }
    }

    /**
     * Records a failed login attempt.
     */
    public void recordFailedAttempt(String email, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt(email, ipAddress, false);
        loginAttemptDao.save(attempt);
        logger.debug("Recorded failed login attempt for: {}", email);
    }

    /**
     * Records a successful login.
     */
    public void recordSuccessfulLogin(String email, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt(email, ipAddress, true);
        loginAttemptDao.save(attempt);
    }
}

