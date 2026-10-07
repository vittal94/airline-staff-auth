package com.airline.airlinebackend.security;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.dao.RememberMeTokenDAO;
import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.model.RememberMeToken;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.util.CookieUtil;
import com.airline.airlinebackend.util.SecureTokenGenerator;
import com.airline.airlinebackend.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Remember me token service with series-based theft detection
 */
public class RememberMeService {
    private static final Logger logger = LoggerFactory.getLogger(RememberMeService.class);

    private static final String COOKIE_SEPARATOR = ":";

    private final RememberMeTokenDAO tokenDAO;
    private final UserDAO userDAO;
    private final AppConfig appConfig;

    public RememberMeService() {
        this(new RememberMeTokenDAO(), new UserDAO(), AppConfig.getInstance());
    }

    public RememberMeService(RememberMeTokenDAO tokenDAO, UserDAO userDAO, AppConfig appConfig) {
        this.tokenDAO = tokenDAO;
        this.userDAO = userDAO;
        this.appConfig = appConfig;
    }

    /**
     * Creates a new  remember-me token for the user
     */
    public void createRememberMeToken(
            HttpServletRequest request,
            HttpServletResponse response,
            UUID userId) {
        String series = SecureTokenGenerator.generateSeries();
        String token = SecureTokenGenerator.generateRememberMeToken();
        String tokenHash = PasswordEncoder.hashToken(token);

        Instant expires = Instant.now()
                .plus(appConfig.getRememberMeDurationHours(), ChronoUnit.HOURS);

        RememberMeToken rememberMeToken = new RememberMeToken(
                series,
                userId,
                tokenHash,
                ServletUtil.getClientIP(request),
                ServletUtil.getUserAgent(request),
                expires
        );
        tokenDAO.save(rememberMeToken);

        // Set cookie with series:token format
        String cookieValue = series + COOKIE_SEPARATOR + token;
        int maxAgeSeconds = appConfig.getRememberMeDurationHours() * 3600;
        CookieUtil.setSecureCookie(response, appConfig.getRememberMeCookieName(),
                cookieValue, maxAgeSeconds);

        logger.debug("Created remember-me token for user: {}", userId);
    }

    /**
     * Validates and rotates remember-me token
     * Returns the user if valid, empty if invalid
     * Detects token theft and invalidates all tokens if detected
     */
    public Optional<User> validateAndRotate(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> cookieValue = CookieUtil.getCookieValue(request, appConfig.getRememberMeCookieName());

        if (cookieValue.isEmpty()) return Optional.empty();

        String [] parts = cookieValue.get().split(COOKIE_SEPARATOR);
        if (parts.length != 2) {
            logger.warn("Invalid remember-me cookie format");
            clearRememberMeCookie(response);
            return Optional.empty();
        }
        String series = parts[0];
        String presentedToken = parts[1];

        Optional<RememberMeToken> storedToken = tokenDAO.findBySeries(series);
        if (storedToken.isEmpty()) {
            logger.debug("Remember-me series not found: {}", series);
            clearRememberMeCookie(response);
            return Optional.empty();
        }

        RememberMeToken token = storedToken.get();
        // Check if expired
        if (token.isExpired()) {
            logger.debug("Remember-me token expired");
            tokenDAO.deleteBySeries(series);
            clearRememberMeCookie(response);
            return Optional.empty();
        }

        // Verify token
        if (!PasswordEncoder.verifyToken(presentedToken, token.getTokenHash())) {
            // Token mismatch with valid series = potential theft!
            logger.warn("Remember-me token theft detected for user: {}. Invalidating all tokens.",
                    token.getUserId());
            tokenDAO.deleteByUserId(token.getUserId());
            clearRememberMeCookie(response);
            return Optional.empty();
        }

        // Token valid - rotate it
        String newToken = SecureTokenGenerator.generateRememberMeToken();
        String newTokenHash = PasswordEncoder.hashToken(newToken);
        Instant newExpiresAt = Instant.now().plus(appConfig.getRememberMeDurationHours(), ChronoUnit.HOURS);

        tokenDAO.update(series, newTokenHash, newExpiresAt);

        // Update cookie with new token
        String newCookieValue = series + COOKIE_SEPARATOR + newToken;
        int maxAgeSeconds = appConfig.getRememberMeDurationHours() * 3600;
        CookieUtil.setSecureCookie(response, appConfig.getRememberMeCookieName(),
                newCookieValue, maxAgeSeconds);

        logger.debug("Remember-me token rotated for user: {}", token.getUserId());

        return userDAO.findById(token.getUserId());
    }

    /**
     * Clears remember-me token for logout.
     */
    public void clearRememberMe(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> cookieValue = CookieUtil.getCookieValue(request,
                appConfig.getRememberMeCookieName());

        if (cookieValue.isPresent()) {
            String[] parts = cookieValue.get().split(COOKIE_SEPARATOR);
            if (parts.length >= 1) {
                tokenDAO.deleteBySeries(parts[0]);
            }
        }

        clearRememberMeCookie(response);
    }

    /**
     * Clears all remember-me tokens for a user (e.g., on password change).
     */
    public void clearAllUserTokens(UUID userId) {
        tokenDAO.deleteByUserId(userId);
        logger.debug("Cleared all remember-me tokens for user: {}", userId);
    }

    private void clearRememberMeCookie(HttpServletResponse response) {
        CookieUtil.deleteCookie(response, appConfig.getRememberMeCookieName());
    }
}
