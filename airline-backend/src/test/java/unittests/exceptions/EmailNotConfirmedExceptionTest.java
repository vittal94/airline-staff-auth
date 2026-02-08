package unittests.exceptions;

import com.airline.airlinebackend.exception.BaseException;
import com.airline.airlinebackend.exception.EmailNotConfirmedException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailNotConfirmedExceptionTest {

    @Test
    @DisplayName("Create exception with default constructor")
    public void testCreateExceptionWithDefaultConstructor() {
        EmailNotConfirmedException exception = new EmailNotConfirmedException();

        assertNotNull(exception);
        assertEquals("Email address not confirmed. Please check your inbox", exception.getMessage());
        assertEquals("EMAIL_NOT_CONFIRMED", exception.getErrorCode());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    @DisplayName("Should extend BaseException and RuntimeException")
    public void testInheritance() {
        EmailNotConfirmedException exception = new EmailNotConfirmedException();

        assertNotNull(exception);
        assertInstanceOf(BaseException.class, exception);
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    @DisplayName("Should be throwable and catchable")
    public void testShouldBeThrowableAndCatchable() {
        assertThrows(EmailNotConfirmedException.class, () -> {
            throw new EmailNotConfirmedException();
        });
    }

    @Test
    @DisplayName("Should have correct HTTP status code 403")
    public void testHttpStatusCode() {
        EmailNotConfirmedException exception = new EmailNotConfirmedException();

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    @DisplayName("Should be catchable as BaseException")
    public void testCatchableAsBaseException() {
        assertThrows(BaseException.class, () -> {
            throw new EmailNotConfirmedException();
        });
    }
}
