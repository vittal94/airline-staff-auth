package integrationtests.DAO.emailConfiramtionTokenDAO;

import com.airline.airlinebackend.model.EmailConfirmationToken;

import java.time.Instant;
import java.util.UUID;

public class EmailConfirmationTestDataBuilder {
    private UUID id;
    private UUID userId;
    private String tokenHash;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastUsedAt;

    private EmailConfirmationTestDataBuilder() {
        id = UUID.randomUUID();
        tokenHash = UUID.randomUUID().toString();
        createdAt = Instant.now();
        expiresAt = Instant.now().plusSeconds(60);
        lastUsedAt = Instant.now();
    }

    public static EmailConfirmationTestDataBuilder aEmailConfirmationToken() {
        return new EmailConfirmationTestDataBuilder();
    }

    public EmailConfirmationTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public EmailConfirmationTestDataBuilder withUserId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public EmailConfirmationTestDataBuilder withTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
        return this;
    }

    public EmailConfirmationTestDataBuilder withCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public EmailConfirmationTestDataBuilder withExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public EmailConfirmationTestDataBuilder withLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
        return this;
    }

    public EmailConfirmationToken build() {
        EmailConfirmationToken emailConfirmationToken = new EmailConfirmationToken();
        emailConfirmationToken.setId(id);
        emailConfirmationToken.setUserId(userId);
        emailConfirmationToken.setTokenHash(tokenHash);
        emailConfirmationToken.setCreatedAt(createdAt);
        emailConfirmationToken.setExpiresAt(expiresAt);
        emailConfirmationToken.setLastUsedAt(lastUsedAt);
        return emailConfirmationToken;
    }
}
