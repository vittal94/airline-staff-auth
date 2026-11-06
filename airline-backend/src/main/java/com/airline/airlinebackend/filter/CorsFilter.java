package com.airline.airlinebackend.filter;

import com.airline.airlinebackend.config.AppConfig;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * CORS filter for cross-origin requests
 */
@WebFilter(urlPatterns = "/api/*")
public class CorsFilter implements Filter {
    private AppConfig config;
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.config = AppConfig.getInstance();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        //Get origin from request
        String origin = request.getHeader("Origin");

        // The response differs by origin, so shared caches must not reuse it across origins
        if (origin != null) {
            response.setHeader("Vary", "Origin");
        }

        //For development allow local host origin (http/https, any port, exact host)
        if (isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers",
                    "Content-Type, " + config.getCsrfHeaderName() + ", Authorization");
            response.setHeader("Access-Control-Max-Age", "3600");
        }
        //Handle preflight request
        if("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        filterChain.doFilter(request, response);

    }

    /**
     * Dev allowance: only the exact host "localhost" over http or https, any port.
     * Parsing the origin (instead of startsWith) also rejects lookalikes such as
     * http://localhost.evil.com and http://localhost@evil.com.
     */
    private boolean isAllowedOrigin(String origin) {
        if (origin == null) {
            return false;
        }
        try {
            URI uri = new URI(origin);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getUserInfo() != null) {
                return false;
            }
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && "localhost".equalsIgnoreCase(host);
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
