package integrationtests.DAO.UserDao;

import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.emums.Role;
import com.airline.airlinebackend.model.emums.UserStatus;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class UserTestDataBuilder {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private UUID id;
    private String email;
    private String passwordHash;
    private String name;
    private Role role;
    private UserStatus status;
    private boolean passwordChangeRequired;
    private boolean firstLoginCompleted;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;

    private UserTestDataBuilder() {
        int seq = COUNTER.getAndIncrement();
        this.id = UUID.randomUUID();
        this.email = "user" + seq + "@example.com";
        this.passwordHash = "$2a$10$hashedpassword" + seq;
        this.name = "Test User" + seq;
        this.role = Role.CUSTOMER_MANAGER;
        this.status = UserStatus.ACTIVE;
        this.passwordChangeRequired = false;
        this.firstLoginCompleted = false;
        this.createdAt = Instant.now();
    }

    public static UserTestDataBuilder aUser() {
        return new UserTestDataBuilder();
    }

    public static UserTestDataBuilder anAdmin() {
        return new UserTestDataBuilder().withRole(Role.ADMIN);
    }

    public static UserTestDataBuilder aPendingUser() {
        return new UserTestDataBuilder()
                .withStatus(UserStatus.PENDING_EMAIL_CONFIRMATION)
                .withPasswordChangeRequired(true);
    }

    public UserTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public UserTestDataBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public UserTestDataBuilder withPasswordHash(String hash) {
        this.passwordHash = hash;
        return this;
    }

    public UserTestDataBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public UserTestDataBuilder withRole(Role role) {
        this.role = role;
        return this;
    }

    public UserTestDataBuilder withStatus(UserStatus status) {
        this.status = status;
        return this;
    }

    public UserTestDataBuilder withPasswordChangeRequired(boolean required) {
        this.passwordChangeRequired = required;
        return this;
    }

    public UserTestDataBuilder withFirstLoginCompleted(boolean completed) {
        this.firstLoginCompleted = completed;
        return this;
    }

    public UserTestDataBuilder withCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public UserTestDataBuilder withUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public UserTestDataBuilder withLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
        return this;
    }

    public User build() {
        return User.builder()
                .id(id)
                .email(email)
                .name(name)
                .role(role)
                .passwordHash(passwordHash)
                .status(status)
                .passwordChangeRequired(passwordChangeRequired)
                .lastLoginAt(lastLoginAt)
                .firstLoginCompleted(firstLoginCompleted)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
