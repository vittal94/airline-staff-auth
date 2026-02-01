package com.airline.airlinebackend.dto.response;

import com.airline.airlinebackend.model.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class UserResponse {
    private UUID id;
    private String name;
    private String email;
    private String role;
    private String status;
    private Set<String> permissions;
    private boolean passwordChangeRequired;
    private boolean firstLoginCompleted;
    private Instant lastLoginAt;
    private Instant createdAt;

    public UserResponse() {}

    public static UserResponse fromUser(User user) {
        UserResponse userResponse = new UserResponse();
        userResponse.id = user.getId();
        userResponse.name = user.getName();
        userResponse.email = user.getEmail();
        userResponse.role = user.getRole().toString();
        userResponse.status = user.getStatus().toString();
        userResponse.createdAt = user.getCreatedAt();
        userResponse.lastLoginAt = user.getLastLoginAt();
        userResponse.firstLoginCompleted = user.isFirstLoginCompleted();
        userResponse.passwordChangeRequired = user.isPasswordChangeRequired();
        userResponse.permissions = user.getRole()
                .getPermissions()
                .stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        return userResponse;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public boolean isFirstLoginCompleted() {
        return firstLoginCompleted;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
