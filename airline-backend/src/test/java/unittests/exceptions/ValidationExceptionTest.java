package unittests.exceptions;

import com.airline.airlinebackend.dto.response.ErrorResponse;
import com.airline.airlinebackend.exception.BaseException;
import com.airline.airlinebackend.exception.ValidationException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationExceptionTest {
    @Test
    @DisplayName("Create exception with first constructor (message only)")
    void createExceptionWithFirstConstructor() {
        ValidationException valException = new ValidationException("Validation failed");

        assertAll(
                () -> assertNotNull(valException),
                () -> assertEquals("Validation failed", valException.getMessage()),
                () -> assertEquals("VALIDATION_ERROR", valException.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_BAD_REQUEST, valException.getHttpStatus()),
                () -> assertNull(valException.getFieldErrors())
        );
    }

    @Test
    @DisplayName("Create exception with second constructor")
    void createExceptionWithSecondConstructor() {
        List<ErrorResponse.FieldError> fieldErrors = List.of(
                new ErrorResponse.FieldError("password","must contain symbols")
        );
        ValidationException valException = new ValidationException("Test", fieldErrors);

        assertAll(
                () -> assertNotNull(fieldErrors),
                () -> assertNotNull(valException),
                () -> assertEquals("Test", valException.getMessage()),
                () -> assertEquals("VALIDATION_ERROR", valException.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_BAD_REQUEST, valException.getHttpStatus()),
                () -> assertEquals(fieldErrors.size(), valException.getFieldErrors().size()),
                () -> assertEquals(fieldErrors, valException.getFieldErrors())
        );
    }

    @Test
    @DisplayName("Assert throws")
    void assertThrow() {
        ValidationException valException = assertThrows(
                ValidationException.class, () -> {
                    throw new ValidationException("Validation error");
                });
        assertEquals("Validation error", valException.getMessage());
    }

    @Test
    @DisplayName("Create exception with null message (first constructor)")
    void createWithNullMessageFirstConstructor() {
        ValidationException valException = new ValidationException(null);
        
        assertAll(
                () -> assertNotNull(valException),
                () -> assertNull(valException.getMessage()),
                () -> assertEquals("VALIDATION_ERROR", valException.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_BAD_REQUEST, valException.getHttpStatus()),
                () -> assertNull(valException.getFieldErrors())
        );
    }

    @Test
    @DisplayName("Create exception with null message (second constructor)")
    void createWithNullMessageSecondConstructor() {
        List<ErrorResponse.FieldError> fieldErrors = List.of(
                new ErrorResponse.FieldError("email", "Invalid email format")
        );
        ValidationException valException = new ValidationException(null, fieldErrors);
        
        assertAll(
                () -> assertNotNull(valException),
                () -> assertNull(valException.getMessage()),
                () -> assertEquals("VALIDATION_ERROR", valException.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_BAD_REQUEST, valException.getHttpStatus()),
                () -> assertNotNull(valException.getFieldErrors()),
                () -> assertEquals(fieldErrors, valException.getFieldErrors())
        );
    }

    @Test
    @DisplayName("Test inheritance, ValidationException should be instance of BaseException")
    void testInheritance() {
        ValidationException valException = new ValidationException("Validation error");

        assertAll(
                () -> assertNotNull(valException),
                () -> assertInstanceOf(BaseException.class, valException)
        );
    }

    @Test
    @DisplayName("Create exception with null fieldErrors explicitly")
    void createWithNullFieldErrors() {
        ValidationException valException = new ValidationException("Validation error", null);
        
        assertAll(
                () -> assertNotNull(valException),
                () -> assertEquals("Validation error", valException.getMessage()),
                () -> assertEquals("VALIDATION_ERROR", valException.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_BAD_REQUEST, valException.getHttpStatus()),
                () -> assertNull(valException.getFieldErrors())
        );
    }

    @Test
    @DisplayName("Create exception with empty fieldErrors list")
    void createWithEmptyFieldErrors() {
        List<ErrorResponse.FieldError> emptyFieldErrors = new ArrayList<>();
        ValidationException valException = new ValidationException("Validation error", emptyFieldErrors);
        
        assertAll(
                () -> assertNotNull(valException),
                () -> assertEquals("Validation error", valException.getMessage()),
                () -> assertEquals("VALIDATION_ERROR", valException.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_BAD_REQUEST, valException.getHttpStatus()),
                () -> assertNotNull(valException.getFieldErrors()),
                () -> assertTrue(valException.getFieldErrors().isEmpty()),
                () -> assertEquals(0, valException.getFieldErrors().size())
        );
    }

    @Test
    @DisplayName("Create exception with multiple field errors")
    void createWithMultipleFieldErrors() {
        List<ErrorResponse.FieldError> fieldErrors = List.of(
                new ErrorResponse.FieldError("email", "Invalid email format"),
                new ErrorResponse.FieldError("password", "Password too short"),
                new ErrorResponse.FieldError("username", "Username already exists")
        );
        ValidationException valException = new ValidationException("Multiple validation errors", fieldErrors);
        
        assertAll(
                () -> assertNotNull(valException),
                () -> assertEquals("Multiple validation errors", valException.getMessage()),
                () -> assertEquals("VALIDATION_ERROR", valException.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_BAD_REQUEST, valException.getHttpStatus()),
                () -> assertNotNull(valException.getFieldErrors()),
                () -> assertEquals(3, valException.getFieldErrors().size()),
                () -> assertEquals(fieldErrors, valException.getFieldErrors())
        );
    }
}
