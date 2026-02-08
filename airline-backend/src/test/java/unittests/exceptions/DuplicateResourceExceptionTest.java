package unittests.exceptions;

import com.airline.airlinebackend.exception.BaseException;
import com.airline.airlinebackend.exception.DuplicateResourceException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateResourceExceptionTest {

    @Test
    @DisplayName("Create exception with message constructor")
    public void testCreateExceptionWithMessageConstructor() {
        DuplicateResourceException exception = new DuplicateResourceException("Email already exists");

        assertNotNull(exception);
        assertEquals("Email already exists", exception.getMessage());
        assertEquals("DUPLICATE_RESOURCE", exception.getErrorCode());
        assertEquals(HttpServletResponse.SC_CONFLICT, exception.getHttpStatus());
    }

    @Test
    @DisplayName("Should extend BaseException and RuntimeException")
    public void testInheritance() {
        DuplicateResourceException exception = new DuplicateResourceException("Resource exists");

        assertNotNull(exception);
        assertInstanceOf(BaseException.class, exception);
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    @DisplayName("Should be throwable and catchable")
    public void testShouldBeThrowableAndCatchable() {
        assertThrows(DuplicateResourceException.class, () -> {
            throw new DuplicateResourceException("Duplicate found");
        });
    }

    @Test
    @DisplayName("Should have correct HTTP status code 409")
    public void testHttpStatusCode() {
        DuplicateResourceException exception = new DuplicateResourceException("Duplicate resource");

        assertEquals(409, exception.getHttpStatus());
    }

    @Test
    @DisplayName("Should be catchable as BaseException")
    public void testCatchableAsBaseException() {
        assertThrows(BaseException.class, () -> {
            throw new DuplicateResourceException("Test duplicate");
        });
    }

    @Test
    @DisplayName("Test with null message")
    public void testWithNullMessage() {
        DuplicateResourceException exception = new DuplicateResourceException(null);

        assertNotNull(exception);
        assertNull(exception.getMessage());
        assertEquals("DUPLICATE_RESOURCE", exception.getErrorCode());
    }

    @Test
    @DisplayName("Test with different messages")
    public void testWithDifferentMessages() {
        DuplicateResourceException exception1 = new DuplicateResourceException("Email already exists");
        DuplicateResourceException exception2 = new DuplicateResourceException("Username already taken");

        assertEquals("Email already exists", exception1.getMessage());
        assertEquals("Username already taken", exception2.getMessage());
        assertEquals(exception1.getErrorCode(), exception2.getErrorCode());
        assertEquals(exception1.getHttpStatus(), exception2.getHttpStatus());
    }
}
