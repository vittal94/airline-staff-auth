package com.airline.airlinebackend.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Secure token generator utility
 */
public final class SecureTokenGenerator {
    private final static SecureRandom secureRandom = new SecureRandom();
    private final static Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    private SecureTokenGenerator() {}

    /**
     * Generates a cryptographically secure random token
     * @param byteLength Length of random bytes (token will be longer due to Base64 encoding)
     * @return Base64 URL-safe encoded token
     */
    public static String generateToken(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }

    /**
     * Generates session id (32 byte = 256 bits of entropy)
     */
    public static String generateSessionId() {
        return generateToken(32);
    }

    /**
     * Generates a CSRF token (24 bytes = 192 bits of entropy).
     */
    public static String generateCsrfToken() {
        return generateToken(24);
    }

    /**
     * Generates a remember-me series identifier (16 bytes).
     */
    public static String generateSeries() {
        return generateToken(16);
    }

    /**
     * Generates a remember-me token (32 bytes).
     */
    public static String generateRememberMeToken() {
        return generateToken(32);
    }

    /**
     * Generates an email confirmation token (32 bytes).
     */
    public static String generateEmailToken() {
        return generateToken(32);
    }
}
