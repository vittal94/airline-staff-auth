package com.airline.airlinebackend.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class Session {
    private String id;
    private UUID userId;
    private String userAgent;
    private String ipAddress;
    private Map<String, Object> sessionData;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private Instant expiresAt;

    public Session() {}

    public Session(String id, UUID userId, String userAgent, String ipAddress,
                   Map<String, Object> sessionData, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.sessionData = sessionData;
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Map<String, Object> getSessionData() {
        return sessionData;
    }

    public void setSessionData(Map<String, Object> sessionData) {
        this.sessionData = sessionData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
