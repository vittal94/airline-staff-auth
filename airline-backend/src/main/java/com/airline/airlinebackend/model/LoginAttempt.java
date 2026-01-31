package com.airline.airlinebackend.model;

// Login attempt for rate limiting

import java.time.Instant;

public class LoginAttempt {
    private Long id;
    private String email;
    private String ipAddress;
    private Instant attemptedAt;
    private boolean success;

    public LoginAttempt() {}

    public LoginAttempt(String email, String ipAddress, boolean success) {
        this.email = email;
        this.ipAddress = ipAddress;
        this.success = success;
        this.attemptedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
