package com.airline.airlinebackend.filter;

import com.airline.airlinebackend.dto.response.ErrorResponse;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.enums.Role;
import com.airline.airlinebackend.security.PostgresSessionStore;
import com.airline.airlinebackend.service.AuthService;
import com.airline.airlinebackend.util.JsonUtil;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Main security filter for authentication and authorization
 */
@WebFilter(urlPatterns = "/api/*")
public class SecurityFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityFilter.class);

    //Public endpoint that don't require authentication
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/confirm-email",
            "/api/auth/resend-confirmation",
            "/api/health",
            "/api/home"
    );

    //Role-base endpoint access
    private static final Map<String, Set<Role>> ENDPOINT_ROLES = new TreeMap<>(
            Comparator.comparingInt(String::length).reversed());

    static {
        // Admin endpoints
        ENDPOINT_ROLES.put("/api/admin", Set.of(Role.ADMIN));
        ENDPOINT_ROLES.put("/api/dashboard/admin", Set.of(Role.ADMIN));

        // Flight manager endpoints (includes admin)
        ENDPOINT_ROLES.put("/api/dashboard/flight-manager",
                Set.of(Role.ADMIN, Role.FLIGHT_MANAGER));

        // Customer manager endpoints (includes admin and flight manager)
        ENDPOINT_ROLES.put("/api/dashboard/customer-manager",
                Set.of(Role.ADMIN, Role.FLIGHT_MANAGER, Role.CUSTOMER_MANAGER));

        // Cabinet is accessible to all authenticated users
        ENDPOINT_ROLES.put("/api/cabinet",
                Set.of(Role.ADMIN, Role.FLIGHT_MANAGER, Role.CUSTOMER_MANAGER));
    }

    //Endpoints that require a valid session but no specific role
    private static final Set<String> ANY_ROLE_ENDPOINTS = Set.of(
            "/api/auth/logout",
            "/api/cabinet"
    );

    private PostgresSessionStore sessionStore;
    private AuthService authService;

    public SecurityFilter() {}

    //public constructor for testing only
    public SecurityFilter(PostgresSessionStore sessionStore, AuthService authService) {
        this.sessionStore = sessionStore;
        this.authService = authService;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        //Guard: init() runs after the DI constructor too (tests, programmatic
        //registration) — don't clobber already-injected dependencies
        if (sessionStore == null) {
            this.sessionStore = new PostgresSessionStore();
        }
        if (authService == null) {
            this.authService = new AuthService();
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        String path = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();

        if (path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        //Set security headers
        setSecurityHeaders(httpResponse);

        try {
            //Check if an endpoint is public
            if(isPublicEndpoint(path)) {
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }
            //Try to get current user form session
            Optional<User> optUser = authService.getCurrentUser(httpRequest, httpResponse, sessionStore);

            //If no session try to remember-me authentication
            if (optUser.isEmpty()) {
                optUser = authService.tryToRememberMeLogin(httpRequest, httpResponse, sessionStore);
            }

            //Still no user - unauthorized
            if (optUser.isEmpty()) {
                sendUnauthorized(httpResponse, path);
                return;
            }
            User user = optUser.get();

            //Check authorization
            if (!isAuthorized(path, user.getRole())) {
                sendForbidden(httpResponse, path);
                return;
            }

            //Store user in request attribute for use in servlets
            httpRequest.setAttribute("user", user);
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            //Cleanup thread local session cash
            PostgresSessionStore.clearCurrentSession();
        }
    }

    private void setSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Content-Security-Policy", "default-src 'self'");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.contains(path);
    }

    private boolean isAuthorized(String path, Role role) {
        //TreeMap is ordered by descending key length → longest (most specific) prefix wins
        for (Map.Entry<String, Set<Role>> entry : ENDPOINT_ROLES.entrySet()) {
            String prefix = entry.getKey();
            if (matchesPrefix(path, prefix)) {
                return entry.getValue().contains(role);
            }
        }
        //Session-valid endpoints with no role restriction (logout, cabinet)
        return ANY_ROLE_ENDPOINTS.contains(path);
        //Default-deny: unknown paths are forbidden — a forgotten endpoint
        //returns 403 instead of silently losing protection
    }

    private static boolean matchesPrefix(String path, String prefix) {
        //Segment-boundary match: the key itself or a true subpath of it —
        //never a longer path that merely starts with the same characters ("/api/adminEvil")
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private void sendUnauthorized(HttpServletResponse response, String path) throws IOException {
        logger.debug("Unauthorized access attempt to: {}", path);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json; charset=UTF-8");

        ErrorResponse error = ErrorResponse.of("UNAUTHORIZED",
                "Authentication required").path(path);
        response.getWriter().write(JsonUtil.toJson(error));
    }

    private void sendForbidden(HttpServletResponse response, String path) throws IOException {
        logger.debug("Forbidden access attempt to: {}", path);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json; charset=UTF-8");

        ErrorResponse error = ErrorResponse.of("FORBIDDEN",
                "You don't have permission to access this resource").path(path);
        response.getWriter().write(JsonUtil.toJson(error));
    }
}
