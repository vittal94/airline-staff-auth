package com.airline.airlinebackend.exception;

import jakarta.servlet.http.HttpServletResponse;

public class AuthenticationException extends BaseException {
    public AuthenticationException(String message) {
        super(message,"AUTHENTICATION_FAILED", HttpServletResponse.SC_UNAUTHORIZED);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause, "AUTHENTICATION_FAILED", HttpServletResponse.SC_UNAUTHORIZED);
    }
}
