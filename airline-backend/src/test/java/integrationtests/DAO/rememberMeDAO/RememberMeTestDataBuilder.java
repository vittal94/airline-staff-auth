package integrationtests.DAO.rememberMeDAO;

import com.airline.airlinebackend.model.RememberMeToken;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class RememberMeTestDataBuilder {
    private AtomicInteger counter = new AtomicInteger();
    private String series;
    private UUID userId;
    private String tokenHash;
    private String ipAddress;
    private String userAgent;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant lastUsedAt;

    private RememberMeTestDataBuilder() {
        int sequence = counter.incrementAndGet();
       this.series = UUID.randomUUID().toString();
       this.tokenHash = UUID.randomUUID().toString();
       this.ipAddress = "127.0.0." + sequence;
       this.userAgent = "Safari 5." + sequence;
       this.expiresAt = Instant.now().plusSeconds(30);
       this.createdAt = Instant.now();
       this.lastUsedAt = Instant.now();
    }

    public static RememberMeTestDataBuilder aRememberMe() {
        return new RememberMeTestDataBuilder();
    }

    public RememberMeTestDataBuilder withSeries(String series) {
        this.series = series;
        return this;
    }

    public RememberMeTestDataBuilder withUserId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public RememberMeTestDataBuilder withTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
        return this;
    }

    public RememberMeTestDataBuilder withIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public RememberMeTestDataBuilder withUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public RememberMeTestDataBuilder withExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public RememberMeTestDataBuilder withCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public RememberMeTestDataBuilder withLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
        return this;
    }

    public RememberMeToken build() {
        RememberMeToken token = new RememberMeToken();
        token.setSeries(series);
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setIpAddress(ipAddress);
        token.setUserAgent(userAgent);
        token.setCreatedAt(createdAt);
        token.setExpiresAt(expiresAt);
        token.setLastUsedAt(lastUsedAt);

        return token;
    }
}
