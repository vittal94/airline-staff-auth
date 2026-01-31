package com.airline.airlinebackend.model.emums;

import java.util.Arrays;

public enum UserStatus {
    PENDING_EMAIL_CONFIRMATION("PENDING_EMAIL_CONFIRMATION"),
    PENDING_APPROVAL("PENDING_APPROVAL"),
    ACTIVE("ACTIVE"),
    BLOCKED("BLOCKED");

    private final String value;
    UserStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static UserStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter (userStatus -> userStatus.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown user status:" + value));
    }

    public boolean canLogin() {
        return this == ACTIVE;
    }
}
