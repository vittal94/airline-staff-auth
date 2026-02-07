package com.airline.airlinebackend.exception;

import jakarta.servlet.http.HttpServletResponse;

public class AuthorizationException extends BaseException{
    public AuthorizationException(String message) {
        super(message,"ACCESS_DENIED", HttpServletResponse.SC_FORBIDDEN);
    }
}
