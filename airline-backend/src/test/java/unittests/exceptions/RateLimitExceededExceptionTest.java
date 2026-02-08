package unittests.exceptions;

import com.airline.airlinebackend.exception.BaseException;
import com.airline.airlinebackend.exception.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimitExceededExceptionTest {
    @Test
    @DisplayName("Create exception with normal parameters constructor (message, 12334)")
    void createExceptionWithConstructor() {
        RateLimitExceededException e = new RateLimitExceededException("rate",12334);

        assertAll("Check if properly exception is created",
                () -> assertNotNull(e),
                () -> assertEquals("rate",e.getMessage()),
                () -> assertEquals("RATE_LIMIT_EXCEEDED", e.getErrorCode()),
                () -> assertEquals(429,e.getHttpStatus()),
                () -> assertEquals(12334,e.getRetryAfterSeconds())
        );
    }

    @Test
    @DisplayName("Create exception with null parameter message (null, 34324)")
    void createExceptionWithNullParameterMessage() {
        RateLimitExceededException e = new RateLimitExceededException(null,34324);

        assertAll("Check if properly exception is created with null message",
                () -> assertNotNull(e),
                () -> assertNull(e.getMessage()),
                () -> assertEquals("RATE_LIMIT_EXCEEDED", e.getErrorCode()),
                () -> assertEquals(429,e.getHttpStatus()),
                () -> assertEquals(34324,e.getRetryAfterSeconds())
        );
    }

    @Test
    @DisplayName("Should inherit BaseException")
    void shouldInheritBaseException() {
        RateLimitExceededException e = new RateLimitExceededException("rate",12334);

        assertAll("Check inheritance",
                () -> assertNotNull(e),
                () -> assertInstanceOf(BaseException.class, e)
        );
    }

    @Test
    @DisplayName("Should throws the exception")
    void shouldThrowException() {
        RateLimitExceededException e = assertThrows(RateLimitExceededException.class,
                () -> { throw new RateLimitExceededException("rate",12334); });
        assertEquals("rate",e.getMessage());
    }
}
