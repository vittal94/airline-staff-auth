package com.airline.airlinebackend.exception;

import jakarta.servlet.http.HttpServletResponse;

public class DuplicateResourceException extends BaseException{
    public DuplicateResourceException(String message) {
        super(message, "DUPLICATE_RESOURCE", HttpServletResponse.SC_CONFLICT);
    }

    public DuplicateResourceException(String resourceType, String field, String value) {
        super(String.format("%s with %s '%s' already exists.", resourceType, field, value),
                "DUPLICATE_RESOURCE", HttpServletResponse.SC_CONFLICT);
    }
}
