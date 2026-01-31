package com.airline.airlinebackend.model;

import java.time.Instant;
import java.util.UUID;

public class RememberMeToken {
    private String series;
    private UUID userId;
    private String tokenHash;
    private String ipAddress;
    private String userAgent;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant lastUsedAt;

    public RememberMeToken() {}

    public RememberMeToken(String series, UUID userId, String tokenHash, String ipAddress,
                           String userAgent, Instant expiresAt) {
        this.series = series;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.lastUsedAt = Instant.now();
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
