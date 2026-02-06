package com.airline.airlinebackend.exception;

public abstract class BaseException extends RuntimeException {
    private String errorCode;
    private int httpStatus;

    protected BaseException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected BaseException(String message, Throwable cause, String errorCode, int httpStatus) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() { return errorCode; }

    public int getHttpStatus() { return httpStatus; }
}
