package com.airline.airlinebackend.security;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.util.CookieUtil;
import com.airline.airlinebackend.util.SecureTokenGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * CSRF token management service using Double Submit Cookie pattern.
 */
public class CsrfTokenService {

    private static final Logger logger = LoggerFactory.getLogger(CsrfTokenService.class);

    private final AppConfig config;

    public CsrfTokenService() {
        this.config = AppConfig.getInstance();
    }

    /**
     * Generates and sets a new CSRF token.
     */
    public String generateToken(HttpServletResponse response) {
        String token = SecureTokenGenerator.generateCsrfToken();

        // Set CSRF token in a non-HttpOnly cookie (so JavaScript can read it)
        int maxAge = config.getSessionTimeoutMinutes() * 60;
        CookieUtil.setCsrfCookie(response, token, maxAge);

        logger.debug("Generated new CSRF token");
        return token;
    }

    /**
     * Validates CSRF token from request.
     * Compares cookie value with header value.
     */
    public boolean validateToken(HttpServletRequest request) {
        // Get token from cookie
        Optional<String> cookieToken = CookieUtil.getCookieValue(request, config.getCsrfCookieName());

        // Get token from header
        String headerToken = request.getHeader(config.getCsrfHeaderName());

        if (cookieToken.isEmpty() || headerToken == null) {
            logger.debug("CSRF validation failed: missing token (cookie={}, header={})",
                    cookieToken.isPresent(), headerToken != null);
            return false;
        }

        // Constant-time comparison
        boolean valid = constantTimeEquals(cookieToken.get(), headerToken);

        if (!valid) {
            logger.warn("CSRF validation failed: token mismatch");
        }

        return valid;
    }

    /**
     * Refreshes CSRF token (should be called periodically or after sensitive operations).
     */
    public String refreshToken(HttpServletResponse response) {
        return generateToken(response);
    }

    /**
     * Clears CSRF token cookie.
     */
    public void clearToken(HttpServletResponse response) {
        CookieUtil.deleteCookie(response, config.getCsrfCookieName());
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}