package com.airline.airlinebackend.exception;

import jakarta.servlet.http.HttpServletResponse;

public class ResourceNotFoundException extends BaseException{
    public ResourceNotFoundException(String message) {
        super(message,"RESOURCE_NOT_FOUND", HttpServletResponse.SC_NOT_FOUND);
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(String.format("%s not found: %s", resourceType,identifier),
                "RESOURCE_NOT_FOUND", HttpServletResponse.SC_NOT_FOUND);
    }
}
