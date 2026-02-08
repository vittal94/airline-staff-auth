package com.airline.airlinebackend.exception;

import jakarta.servlet.http.HttpServletResponse;

public class EmailNotConfirmedException extends BaseException {
    public EmailNotConfirmedException() {
        super("Email address not confirmed. Please check your inbox",
                "EMAIL_NOT_CONFIRMED", HttpServletResponse.SC_FORBIDDEN);
    }
}
