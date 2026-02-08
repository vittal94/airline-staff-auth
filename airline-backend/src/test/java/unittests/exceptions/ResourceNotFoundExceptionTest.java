package unittests.exceptions;

import com.airline.airlinebackend.exception.BaseException;
import com.airline.airlinebackend.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ResourceNotFoundExceptionTest {
    @Test
    @DisplayName("Create exception with first constructor (message)")
    void createExceptionWithFirstConstructor() {
        ResourceNotFoundException e = new ResourceNotFoundException("resource not found");

        assertAll("Test if exception is created properly",
                () -> assertNotNull(e),
                () -> assertEquals("resource not found", e.getMessage()),
                () -> assertEquals("RESOURCE_NOT_FOUND",e.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_NOT_FOUND,e.getHttpStatus())
        );
    }

    @Test
    @DisplayName("Create exception with second constructor (resourceType, identifier)")
    void createExceptionWithSecondConstructor() {
        ResourceNotFoundException e = new ResourceNotFoundException("Username","id: 1234323");

        assertAll("Test if exception is created properly",
                () -> assertNotNull(e),
                () -> assertEquals("Username not found: id: 1234323", e.getMessage()),
                () -> assertEquals("RESOURCE_NOT_FOUND",e.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_NOT_FOUND,e.getHttpStatus())
        );


    }

    @Test
    @DisplayName("Create with null parameter first constructor")
    void createExceptionWithNullParameterFirstConstructor() {
        ResourceNotFoundException e = new ResourceNotFoundException(null);

        assertAll("test the null message",
                () -> assertNotNull(e),
                () -> assertNull(e.getMessage()),
                () -> assertEquals("RESOURCE_NOT_FOUND",e.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_NOT_FOUND,e.getHttpStatus())
        );
    }

    @Test
    @DisplayName("Create with null parameters second constructor")
    void createExceptionWithNullParametersSecondConstructor() {
        ResourceNotFoundException e = new ResourceNotFoundException(null,null);

        assertAll("test the null message",
                () -> assertNotNull(e),
                () -> assertEquals("null not found: null", e.getMessage()),
                () -> assertEquals("RESOURCE_NOT_FOUND",e.getErrorCode()),
                () -> assertEquals(HttpServletResponse.SC_NOT_FOUND,e.getHttpStatus())
        );
    }

    @Test
    @DisplayName("Should inherit the BaseException")
    void testInheritBaseException() {
        ResourceNotFoundException e = new ResourceNotFoundException("resource not found");

        assertAll("Check if ResourceException inherited BaseException",
                () -> assertNotNull(e),
                () -> assertInstanceOf(BaseException.class, e)
        );
    }

    @Test
    @DisplayName("Should throws exception")
    void testThrowException() {
        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class,
                () -> { throw new ResourceNotFoundException("resource not found"); });
        assertEquals("resource not found", e.getMessage());
    }
}
