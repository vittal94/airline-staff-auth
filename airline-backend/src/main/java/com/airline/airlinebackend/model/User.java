package com.airline.airlinebackend.model;

import com.airline.airlinebackend.model.emums.Role;
import com.airline.airlinebackend.model.emums.UserStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class User {
    private UUID id;
    private String name;
    private String passwordHash;
    private String email;
    private Role role;
    private UserStatus status;
    private boolean passwordChangeRequired;
    private boolean firstLoginCompleted;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;

    public User() {}

    private User(Builder builder) {
        this.createdAt = builder.createdAt;
        this.email = builder.email;
        this.name = builder.name;
        this.id = builder.id;
        this.lastLoginAt = builder.lastLoginAt;
        this.passwordHash = builder.passwordHash;
        this.status = builder.status;
        this.role = builder.role;
        this.updatedAt = builder.updatedAt;
        this.passwordChangeRequired = builder.passwordChangeRequired;
        this.firstLoginCompleted = builder.firstLoginCompleted;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isFirstLoginCompleted() {
        return firstLoginCompleted;
    }

    public void setFirstLoginCompleted(boolean firstLoginCompleted) {
        this.firstLoginCompleted = firstLoginCompleted;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public void setPasswordChangeRequired(boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isLogin() { return status == UserStatus.ACTIVE; }

    public boolean isAdmin() { return role == Role.ADMIN; }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                ", status=" + status +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String name;
        private String passwordHash;
        private String email;
        private Role role;
        private UserStatus status = UserStatus.PENDING_EMAIL_CONFIRMATION;
        private boolean passwordChangeRequired = false;
        private boolean firstLoginCompleted = false;
        private Instant lastLoginAt;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }

        public Builder name(String name) { this.name = name; return this; }

        public Builder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }

        public Builder email(String email) { this.email = email; return this; }

        public Builder role(Role role) { this.role = role; return this; }

        public Builder status(UserStatus status) { this.status = status; return this; }

        public Builder passwordChangeRequired(boolean passwordChangeRequired) {
            this.passwordChangeRequired = passwordChangeRequired; return this;
        }

        public Builder firstLoginCompleted(boolean firstLoginCompleted) {
            this.firstLoginCompleted = firstLoginCompleted; return this;
        }

        public Builder lastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; return this; }

        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public User build() { return new User(this); }

    }
}
