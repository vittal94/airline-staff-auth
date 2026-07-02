package com.airline.airlinebackend.util;

import com.airline.airlinebackend.config.AppConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.Optional;

/**
 * Utility class for reading, setting, and deleting HTTP cookies.
 * <p>
 * Builds {@code Set-Cookie} headers manually to support the {@code SameSite}
 * attribute, which the Servlet Cookie API does not expose directly.
 */
public final class CookieUtil {
    private CookieUtil() {}

    /**
     * Retrieves the value of the first cookie matching the given name from the request.
     *
     * @param request    the HTTP request containing cookies
     * @param cookieName the name of the cookie to look up
     * @return an {@code Optional} containing the cookie value, or empty if not present
     */
    public static Optional<String> getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    /**
     * Sets a secure, HttpOnly cookie on the response. Intended for session
     * and remember-me tokens that must not be readable by JavaScript.
     *
     * @param response      the HTTP response to attach the cookie to
     * @param name          the cookie name
     * @param value         the cookie value
     * @param maxAgeSeconds  cookie lifetime in seconds; {@code 0} deletes immediately, {@code -1} makes it session-scoped
     */
    public static void setSecureCookie(HttpServletResponse response, String name,
                                       String value, int maxAgeSeconds) {
        AppConfig config = AppConfig.getInstance();
        String header = buildCookieHeader(name, value, true, config, maxAgeSeconds);
        response.addHeader("Set-Cookie", header);
    }

    /**
     * Sets a CSRF cookie on the response. Unlike secure cookies, this cookie
     * is <strong>not</strong> HttpOnly so that JavaScript can read it and
     * include its value in subsequent request headers.
     *
     * @param response      the HTTP response to attach the cookie to
     * @param value         the cookie value (CSRF token)
     * @param maxAgeSeconds  cookie lifetime in seconds
     */
    public static void setCsrfCookie(HttpServletResponse response, String value, int maxAgeSeconds) {
        AppConfig config = AppConfig.getInstance();
        String header = buildCookieHeader(config.getCsrfCookieName(), value, false, config, maxAgeSeconds);
        response.addHeader("Set-Cookie", header);
    }

    /**
     * Deletes a cookie by setting its {@code Max-Age} to {@code 0}.
     * Defaults to {@code HttpOnly}, suitable for session and remember-me cookies.
     *
     * @param response   the HTTP response to attach the deletion header to
     * @param cookieName the name of the cookie to delete
     */
    public static void deleteCookie(HttpServletResponse response, String cookieName) {
        deleteCookie(response, cookieName, true);
    }

    /**
     * Deletes a cookie by setting its {@code Max-Age} to {@code 0}.
     *
     * @param response   the HTTP response to attach the deletion header to
     * @param cookieName the name of the cookie to delete
     * @param httpOnly   whether the original cookie was HttpOnly; the deletion
     *                   header must match the original attributes for the
     *                   browser to accept it. Use {@code false} for CSRF cookies.
     */
    public static void deleteCookie(HttpServletResponse response, String cookieName, boolean httpOnly) {
        AppConfig config = AppConfig.getInstance();
        String header = buildCookieHeader(cookieName, "", httpOnly, config, 0);
        response.addHeader("Set-Cookie", header);
    }

    /**
     * Builds a {@code Set-Cookie} header string with the common attributes
     * (Path, HttpOnly, Secure, SameSite, Max-Age) shared by all cookie
     * operations in this utility.
     *
     * @param name          the cookie name
     * @param value         the cookie value (empty string when deleting)
     * @param httpOnly      whether to include the {@code HttpOnly} flag
     * @param config        the application config providing Secure and SameSite settings
     * @param maxAgeSeconds  cookie lifetime in seconds; {@code 0} deletes, {@code -1} is session-scoped
     * @return a fully formed {@code Set-Cookie} header value
     */
    private static String buildCookieHeader(String name, String value, boolean httpOnly,
                                           AppConfig config, int maxAgeSeconds) {
        StringBuilder header = new StringBuilder();
        header.append(name).append("=").append(value);
        header.append("; Path=/");
        if (httpOnly) {
            header.append("; HttpOnly");
        }
        if (config.isSessionCookieSecure()) {
            header.append("; Secure");
        }
        header.append("; SameSite=").append(config.getSessionCookieSameSite());
        header.append("; Max-Age=").append(maxAgeSeconds);
        return header.toString();
    }
}