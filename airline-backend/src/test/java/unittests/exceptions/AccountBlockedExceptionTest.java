package unittests.exceptions;

import com.airline.airlinebackend.exception.AccountBlockedException;
import com.airline.airlinebackend.exception.BaseException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountBlockedExceptionTest {

    @Test
    @DisplayName("Create exception with default constructor")
    public void testCreateExceptionWithDefaultConstructor() {
        AccountBlockedException exception = new AccountBlockedException();

        assertNotNull(exception);
        assertEquals("Account is blocked. Please contact administrator.", exception.getMessage());
        assertEquals("ACCOUNT_BLOCKED", exception.getErrorCode());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    @DisplayName("Should extend BaseException and RuntimeException")
    public void testInheritance() {
        AccountBlockedException exception = new AccountBlockedException();

        assertNotNull(exception);
        assertInstanceOf(BaseException.class, exception);
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    @DisplayName("Should be throwable and catchable")
    public void testShouldBeThrowableAndCatchable() {
        assertThrows(AccountBlockedException.class, () -> {
            throw new AccountBlockedException();
        });
    }

    @Test
    @DisplayName("Should have correct HTTP status code 403")
    public void testHttpStatusCode() {
        AccountBlockedException exception = new AccountBlockedException();

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    @DisplayName("Should be catchable as BaseException")
    public void testCatchableAsBaseException() {
        assertThrows(BaseException.class, () -> {
            throw new AccountBlockedException();
        });
    }
}
