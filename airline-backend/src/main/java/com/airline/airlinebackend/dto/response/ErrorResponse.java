package com.airline.airlinebackend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String message;
    private String error;
    private boolean success = false;
    private List<FieldError> fieldErrors;
    private Long timestamp;
    private String path;

    public ErrorResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public ErrorResponse(String error, String message) {
        this();
        this.error = error;
        this.message = message;
    }

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message);
    }

    public ErrorResponse path(String path) {
        this.path = path;
        return this;
    }

    public ErrorResponse fieldErrors(List<FieldError> fieldErrors) {
        this.fieldErrors = fieldErrors;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public String getError() {
        return error;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public String getPath() {
        return path;
    }

    // Field specific validation error
    public static class FieldError {
        private String field;
        private String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}
