package com.airline.airlinebackend.util;

import com.airline.airlinebackend.dto.response.ApiResponse;
import com.airline.airlinebackend.dto.response.ErrorResponse;
import com.airline.airlinebackend.exception.BaseException;
import com.airline.airlinebackend.exception.RateLimitExceededException;
import com.airline.airlinebackend.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Servlets utility methods for response/request handling
 */
public final class ServletUtil {
    private final static Logger logger = LoggerFactory.getLogger(ServletUtil.class);

    private ServletUtil() {}

    /**
     * Reads json body from request and deserializes to special types
     */
    public static <T> T readJsonBody(HttpServletRequest req, Class<T> clazz) throws IOException {
        return JsonUtil.fromJson(req.getInputStream(), clazz);
    }

    /**
     * Writes json response
     */
    public static void writeJsonResponse(HttpServletResponse resp,int status, Object obj)
            throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(JsonUtil.toJson(obj));
    }

    /**
     * Writes success response
     */
    public static <T> void writeSuccess(HttpServletResponse resp, T data)
        throws IOException {
        writeJsonResponse(resp, HttpServletResponse.SC_OK, ApiResponse.success(data));
    }

    /**
     * Writes success with message
     */
    public static void writeSuccess(HttpServletResponse resp, String msg)
        throws IOException {
        writeJsonResponse(resp, HttpServletResponse.SC_OK, ApiResponse.success(msg));
    }

    /**
     * Writes created response (201).
     */
    public static <T> void writeCreated(HttpServletResponse response, T data) throws IOException {
        writeJsonResponse(response, HttpServletResponse.SC_CREATED, ApiResponse.success(data));
    }

    /**
     * Writes error form exception
     */
    public static void writeError(HttpServletResponse resp, HttpServletRequest req, Exception ex)
        throws IOException {
        if (ex instanceof BaseException baseException) {
            ErrorResponse error = ErrorResponse.of(
                            baseException.getErrorCode(),
                            baseException.getMessage())
                    .path(req.getRequestURI());

            if (ex instanceof ValidationException validationException) {
                error.fieldErrors(validationException.getFieldErrors());
            }
            if (ex instanceof RateLimitExceededException rateLimitExceededException) {
                resp.setHeader("Retry-After",
                        String.valueOf(rateLimitExceededException.getRetryAfterSeconds()));
            }
            writeJsonResponse(resp, baseException.getHttpStatus(), error);
        } else {
            logger.error("Unexpected error", ex);
            ErrorResponse error = ErrorResponse.of("INTERNAL_ERROR",
                                                "An unexpected error occurred")
                    .path(req.getRequestURI());

            writeJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
        }
    }

    /**
     * Gets client IP address considering proxies
     */
    public static String getClientIP(HttpServletRequest req) {
        String xForwardedFor = req.getHeader("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = req.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return req.getRemoteAddr();
    }

    /**
     * Gets User-Agent header (truncated for storage)
     */
    public static String getUserAgent(HttpServletRequest req) {
        String userAgent = req.getHeader("User-Agent");

        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        if (userAgent.length() > 500) {
            return userAgent.substring(0, 500);
        }
        return userAgent;
    }

    /**
     * Extracts path parameter from URI.
     * Example: /api/users/123 with pattern /api/users/ returns "123"
     */
    public static String extractPathParam(HttpServletRequest request, String basePath) {
        String path = request.getPathInfo();
        if (path == null) {
            String requestUri = request.getRequestURI();
            if (requestUri == null) {
                return null;
            }
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
                path = requestUri.substring(contextPath.length());
            } else {
                path = requestUri;
            }
        }

        if (path.startsWith(basePath)) {
            String param = path.substring(basePath.length());
            // Remove trailing slash if present
            if (param.endsWith("/")) {
                param = param.substring(0, param.length() - 1);
            }
            return param.isEmpty() ? null : param;
        }
        return null;
    }
}
