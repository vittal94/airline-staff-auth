package unittests.exceptions;

import com.airline.airlinebackend.exception.AuthorizationException;
import com.airline.airlinebackend.exception.BaseException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AuthorizationExceptionTest {
    @Test
    @DisplayName("Create exception with constructor")
    void createExceptionWithConstructor() {
        BaseException authzException = new AuthorizationException("Authorization exception");

        assertAll(
                () -> assertNotNull(authzException),
                () -> assertEquals("Authorization exception", authzException.getMessage()),
                () -> assertEquals("ACCESS_DENIED", authzException.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_FORBIDDEN, authzException.getHttpStatus())
        );
    }

    @Test
    @DisplayName("Assert throws")
    void assertThrow() {
        AuthorizationException authzException = assertThrows(
                AuthorizationException.class, () -> {
                throw new AuthorizationException("Authorization exception");
                });
        assertEquals("Authorization exception", authzException.getMessage());

    }

    @Test
    @DisplayName("Create exception with null value")
    void createWithNullValue() {
        BaseException authzException = new AuthorizationException(null);
        assertAll(
                () -> assertNotNull(authzException),
                () -> assertNull(authzException.getMessage()),
                () -> assertEquals("ACCESS_DENIED", authzException.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_FORBIDDEN, authzException.getHttpStatus())
        );
    }

    @Test
    @DisplayName("Test inheritance, AuthorizationException should be instance of BaseException")
    void testInheritance() {
        AuthorizationException authzException = new AuthorizationException("Authorization exception");

        assertAll(
                () -> assertNotNull(authzException),
                () -> assertInstanceOf(BaseException.class, authzException)
        );
    }
}
