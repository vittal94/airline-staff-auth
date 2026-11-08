package com.airline.airlinebackend.filter;

import com.airline.airlinebackend.util.XssSanitizer;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Filter to sanitize request parameters for XSS prevention.
 */
@WebFilter(urlPatterns = "/api/*")
public class XssFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(XssFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        chain.doFilter(new XssRequestWrapper(httpRequest), response);
    }

    /**
     * Request wrapper that sanitizes parameters.
     */
    private static class XssRequestWrapper extends HttpServletRequestWrapper {

        public XssRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return sanitize(value);
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) {
                return null;
            }

            String[] sanitizedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitizedValues[i] = sanitize(values[i]);
            }
            return sanitizedValues;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> originalMap = super.getParameterMap();
            Map<String, String[]> sanitizedMap = new HashMap<>();

            for (Map.Entry<String, String[]> entry : originalMap.entrySet()) {
                String[] values = entry.getValue();
                String[] sanitizedValues = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    sanitizedValues[i] = sanitize(values[i]);
                }
                sanitizedMap.put(entry.getKey(), sanitizedValues);
            }

            return Collections.unmodifiableMap(sanitizedMap);
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            // Don't sanitize certain headers
            if ("Content-Type".equalsIgnoreCase(name) ||
                    "Authorization".equalsIgnoreCase(name) ||
                    name.toLowerCase().startsWith("x-")) {
                return value;
            }
            return sanitize(value);
        }

        private String sanitize(String value) {
            if (value == null) {
                return null;
            }

            // Check if value contains potential XSS
            if (XssSanitizer.containsXss(value)) {
                logger.warn("XSS attempt detected and sanitized: {}",
                        value.substring(0, Math.min(100, value.length())));
            }

            return XssSanitizer.sanitize(value);
        }
    }
}
