package com.airline.airlinebackend.filter;

import com.airline.airlinebackend.dto.response.ErrorResponse;
import com.airline.airlinebackend.security.CsrfTokenService;
import com.airline.airlinebackend.util.JsonUtil;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF protection filter using double submit cookie pattern
 */
@WebFilter(urlPatterns = "/api/*")
public class CsrfFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(CsrfFilter.class);

    //Methods that don't require CSRF protection
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    //Endpoint that don't require CSRF protection
    private static final Set<String> EXEMPT_ENDPOINTS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/confirm-email",
            "/api/auth/resend-confirmation",
            "/api/health"
    );

    private CsrfTokenService csrfTokenService;
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.csrfTokenService = new CsrfTokenService();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String method = httpRequest.getMethod();
        String path = httpRequest.getRequestURI();

        // Skip CSRF check for safe methods
        if (SAFE_METHODS.contains(method.toUpperCase())) {
            // Generate CSRF token if not present
            csrfTokenService.generateToken(httpResponse);
            chain.doFilter(request, response);
            return;
        }

        // Skip CSRF check for exempt endpoints
        if (isExemptEndpoint(path)) {
            // Generate CSRF token for response
            csrfTokenService.generateToken(httpResponse);
            chain.doFilter(request, response);
            return;
        }

        // Validate CSRF token
        if (!csrfTokenService.validateToken(httpRequest)) {
            logger.warn("CSRF validation failed for {} {}", method, path);

            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json");

            ErrorResponse error = ErrorResponse.of("CSRF_VALIDATION_FAILED",
                    "CSRF token validation failed");
            httpResponse.getWriter().write(JsonUtil.toJson(error));
            return;
        }

        // Refresh CSRF token after validation
        csrfTokenService.refreshToken(httpResponse);

        chain.doFilter(request, response);
    }

    private boolean isExemptEndpoint(String path) {
        return EXEMPT_ENDPOINTS.contains(path);
    }

    @Override
    public void destroy() {
    }
}
