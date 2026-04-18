package integrationtests.DAO.SessionDAO;

import com.airline.airlinebackend.model.Session;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionTestDataBuilder {

   private static final AtomicInteger COUNTER = new AtomicInteger();

    private String id;
    private UUID userId;
    private String userAgent;
    private String ipAddress;
    private Map<String, Object> sessionData;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private Instant expiresAt;

    private SessionTestDataBuilder() {
        int seq = COUNTER.incrementAndGet();
        id = UUID.randomUUID().toString();
        userAgent = "Mozilla/5." + seq;
        ipAddress = "127.0.0." + seq;
        createdAt = Instant.now();
        lastAccessedAt = Instant.now();
        expiresAt = Instant.now().plusSeconds(60);

    }

    public static SessionTestDataBuilder aSession() {
        return new SessionTestDataBuilder();
    }

    public static SessionTestDataBuilder aUserAgentOpera() {
        return new SessionTestDataBuilder().withUserAgent("Opera/4.3");
    }




    public SessionTestDataBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public SessionTestDataBuilder withUserId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public SessionTestDataBuilder withUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public SessionTestDataBuilder withIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public SessionTestDataBuilder withSessionData(Map<String, Object> sessionData) {
        this.sessionData = sessionData;
        return this;
    }

    public SessionTestDataBuilder withCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public SessionTestDataBuilder withLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
        return this;
    }

    public SessionTestDataBuilder withExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public Session build() {
        Session session = new Session();
        session.setId(id);
        session.setUserId(userId);
        session.setUserAgent(userAgent);
        session.setIpAddress(ipAddress);
        session.setSessionData(sessionData);
        session.setCreatedAt(createdAt);
        session.setLastAccessedAt(lastAccessedAt);
        session.setExpiresAt(expiresAt);

        return session;
    }


}
