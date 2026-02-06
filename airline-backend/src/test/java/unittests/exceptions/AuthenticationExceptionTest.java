package unittests.exceptions;

import com.airline.airlinebackend.exception.AuthenticationException;
import com.airline.airlinebackend.exception.BaseException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationExceptionTest {
    @Test
    @DisplayName("Create exception with first constructor")
    public void testCreateExceptionWithFirstConstructor() {
        BaseException authException = new AuthenticationException("Authentication exception");
        assertNotNull(authException);
        assertEquals("Authentication exception", authException.getMessage());
        assertEquals("AUTHENTICATION_FAILED", authException.getErrorCode());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, authException.getHttpStatus());
    }

    @Test
    @DisplayName("Create exception with second constructor")
    public void testCreateExceptionWithSecondConstructor() {
        Exception cause = new IllegalArgumentException("Illegal argument");
        BaseException authException = new AuthenticationException("Authentication exception",cause);

        assertNotNull(authException);
        assertNotNull(cause);
        assertEquals("Authentication exception", authException.getMessage());
        assertEquals("AUTHENTICATION_FAILED", authException.getErrorCode());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, authException.getHttpStatus());

        assertNotNull(authException.getCause());
        assertEquals(cause, authException.getCause());
        assertEquals("Illegal argument", authException.getCause().getMessage());
    }

    @Test
    @DisplayName("Should extend BaseException and RuntimeException")
    public void testInheritance() {
        BaseException authException = new AuthenticationException("Authentication exception");
        assertNotNull(authException);
        assertInstanceOf(BaseException.class, authException);
        assertInstanceOf(RuntimeException.class, authException);
    }

    @Test
    @DisplayName("Test with null massage")
    public void testWithNullMassage() {
        BaseException authException = new AuthenticationException(null);
        assertNotNull(authException);
        assertNull(authException.getMessage());
        assertEquals("AUTHENTICATION_FAILED", authException.getErrorCode());
    }

    @Test
    @DisplayName("Should be throwable and catchable")
    public void testShouldBeThrowableAndCatchable() {
        assertThrows(AuthenticationException.class, () -> {
            throw new AuthenticationException("Test");
        });
    }

    @Test
    @DisplayName("Should throw different cause exception types")
    public void testShouldThrowDifferentCauseExceptionTypes() {
        NullPointerException npe = new NullPointerException("Test");
        AuthenticationException authException = new AuthenticationException("Test", npe);

        assertInstanceOf(NullPointerException.class, authException.getCause());
    }

}