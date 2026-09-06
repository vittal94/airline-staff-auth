package com.airline.airlinebackend.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.owasp.encoder.Encode;

/**
 * XSS prevention and input sanitization utility.
 */
public final class XssSanitizer {

    private XssSanitizer() {
        // Utility class
    }

    /**
     * Sanitizes input by removing all HTML tags.
     * Use this for plain text fields that should never contain HTML.
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        // Remove all HTML tags
        String cleaned = Jsoup.clean(input, Safelist.none());

        // Also encode any remaining potential XSS vectors
        return cleaned.trim();
    }

    /**
     * Encodes special HTML characters.
     * Use this when you need to preserve the original input but prevent XSS.
     */
    public static String encode(String input) {
        if (input == null) {
            return null;
        }

        return Encode.forHtml(input);
    }

    /**
     * Sanitizes email address (removes any HTML/script content).
     */
    public static String sanitizeEmail(String email) {
        if (email == null) {
            return null;
        }

        // Remove any HTML
        String cleaned = Jsoup.clean(email, Safelist.none());

        // Remove any characters that shouldn't be in an email
        return cleaned.replaceAll("[<>\"'&;]", "").trim().toLowerCase();
    }

    /**
     * Sanitizes name field.
     */
    public static String sanitizeName(String name) {
        if (name == null) {
            return null;
        }
// Remove HTML tags
        String cleaned = Jsoup.clean(name, Safelist.none());

        // Remove potentially dangerous characters but keep legitimate name characters
        return cleaned.replaceAll("[<>\"&;]", "").trim();
    }

    /**
     * Checks if input contains potential XSS content.
     */
    public static boolean containsXss(String input) {
        if (input == null) {
            return false;
        }

        String cleaned = Jsoup.clean(input, Safelist.none());
        return !input.equals(cleaned);
    }
}