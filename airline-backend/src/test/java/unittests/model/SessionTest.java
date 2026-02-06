package unittests.model;

import com.airline.airlinebackend.model.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Session model class tests")
public class SessionTest {
    @Test
    @DisplayName("isExpired return true, set expiration time to minus")
    public void isExpiredReturnTrueSetExpirationTimeToMinus() {
        Session session = new Session();
        session.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        assertTrue(session.isExpired());
    }

    @Test
    @DisplayName("isExpired returns false, set expiration time to plus")
    public void isExpiredReturnFalseSetExpirationTimeToPlus() {
        Session session = new Session();
        session.setExpiresAt(Instant.now().plus(Duration.ofMinutes(1)));
        assertFalse(session.isExpired());
    }
}