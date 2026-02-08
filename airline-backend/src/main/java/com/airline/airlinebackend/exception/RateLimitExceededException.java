package com.airline.airlinebackend.exception;

public class RateLimitExceededException  extends BaseException{
    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message,"RATE_LIMIT_EXCEEDED",429); // 429 too many requests
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
