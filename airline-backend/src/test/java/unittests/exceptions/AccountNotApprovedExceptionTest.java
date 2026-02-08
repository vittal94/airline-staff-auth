package unittests.exceptions;

import com.airline.airlinebackend.exception.AccountNotApprovedException;
import com.airline.airlinebackend.exception.BaseException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountNotApprovedExceptionTest {

    @Test
    @DisplayName("Create exception with default constructor")
    public void testCreateExceptionWithDefaultConstructor() {
        AccountNotApprovedException exception = new AccountNotApprovedException();

        assertNotNull(exception);
        assertEquals("Account pending approval. Please wait for administrator approval.", exception.getMessage());
        assertEquals("ACCOUNT_NOT_APPROVED", exception.getErrorCode());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    @DisplayName("Should extend BaseException and RuntimeException")
    public void testInheritance() {
        AccountNotApprovedException exception = new AccountNotApprovedException();

        assertNotNull(exception);
        assertInstanceOf(BaseException.class, exception);
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    @DisplayName("Should be throwable and catchable")
    public void testShouldBeThrowableAndCatchable() {
        assertThrows(AccountNotApprovedException.class, () -> {
            throw new AccountNotApprovedException();
        });
    }

    @Test
    @DisplayName("Should have correct HTTP status code 403")
    public void testHttpStatusCode() {
        AccountNotApprovedException exception = new AccountNotApprovedException();

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    @DisplayName("Should be catchable as BaseException")
    public void testCatchableAsBaseException() {
        assertThrows(BaseException.class, () -> {
            throw new AccountNotApprovedException();
        });
    }
}
