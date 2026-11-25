package com.airline.airlinebackend.servlet;

import com.airline.airlinebackend.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * Home endpoint - publicly accessible
 */
@WebServlet(name = "HomeServlet", urlPatterns = {"/api/home","/api/health"})
public class HomeServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getRequestURI();

        if (path.endsWith("/health")) {
            ServletUtil.writeSuccess(resp, Map.of(
                    "status", "UP",
                    "timestamp", System.currentTimeMillis()
            ));
        } else {
            ServletUtil.writeSuccess(resp, Map.of(
                    "message", "Welcome to airline staff system",
                    "version", "1.0.0"
            ));
        }
    }
}
