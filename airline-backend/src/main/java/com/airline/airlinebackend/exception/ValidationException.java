package com.airline.airlinebackend.exception;

import com.airline.airlinebackend.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public class ValidationException extends BaseException {
    private List<ErrorResponse.FieldError> fieldErrors;

    public ValidationException(String message) {
        super(message,"VALIDATION_ERROR", HttpServletResponse.SC_BAD_REQUEST);
        this.fieldErrors = null;
    }
    public ValidationException(String message, List<ErrorResponse.FieldError> fieldErrors) {
        super(message,"VALIDATION_ERROR", HttpServletResponse.SC_BAD_REQUEST);
        this.fieldErrors = fieldErrors;
    }

    public List<ErrorResponse.FieldError> getFieldErrors() {return fieldErrors;}
}
