package unittests.util;

import com.airline.airlinebackend.dto.response.ErrorResponse;
import com.airline.airlinebackend.exception.RateLimitExceededException;
import com.airline.airlinebackend.exception.ResourceNotFoundException;
import com.airline.airlinebackend.exception.ValidationException;
import com.airline.airlinebackend.util.JsonUtil;
import com.airline.airlinebackend.util.ServletUtil;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ServletUtil}.
 * <p>
 * All tests use mocked servlet objects and capture written response bodies
 * via an in-memory {@link StringWriter}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServletUtil")
class ServletUtilTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private StringWriter responseBody;
    private PrintWriter responseWriter;

    @BeforeEach
    void setUp() throws IOException {
        responseBody = new StringWriter();
        responseWriter = new PrintWriter(responseBody);
        lenient().when(response.getWriter()).thenReturn(responseWriter);
    }

    // -------------------------------------------------------------------------
    // readJsonBody
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("readJsonBody()")
    class ReadJsonBodyTests {

        @Test
        @DisplayName("should deserialize valid JSON body into target type")
        void readJsonBody_validJson_returnsDeserializedObject() throws IOException {
            // Arrange
            String json = "{\"name\":\"Alice\",\"age\":30}";
            given(request.getInputStream()).willReturn(new ServletInputStreamStub(json));

            // Act
            Person result = ServletUtil.readJsonBody(request, Person.class);

            // Assert
            assertThat(result).extracting(Person::name, Person::age)
                    .containsExactly("Alice", 30);
            then(request).should().getInputStream();
        }

        @Test
        @DisplayName("should propagate IOException when reading input stream fails")
        void readJsonBody_ioException_propagatesIOException() throws IOException {
            // Arrange
            given(request.getInputStream()).willThrow(new IOException("stream broken"));

            // Act & Assert
            assertThatThrownBy(() -> ServletUtil.readJsonBody(request, Person.class))
                    .isInstanceOf(IOException.class)
                    .hasMessage("stream broken");
        }

        @Test
        @DisplayName("should propagate runtime exception for malformed JSON")
        void readJsonBody_malformedJson_propagatesException() throws IOException {
            // Arrange
            String json = "{not valid json";
            given(request.getInputStream()).willReturn(new ServletInputStreamStub(json));

            // Act & Assert
            assertThatThrownBy(() -> ServletUtil.readJsonBody(request, Person.class))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // -------------------------------------------------------------------------
    // writeJsonResponse
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("writeJsonResponse()")
    class WriteJsonResponseTests {

        @Test
        @DisplayName("should set status, content type, encoding and write JSON body")
        void writeJsonResponse_withPayload_writesCorrectResponse() throws IOException {
            // Arrange
            Person payload = new Person("Bob", 25);

            // Act
            ServletUtil.writeJsonResponse(response, HttpServletResponse.SC_ACCEPTED, payload);

            // Assert
            then(response).should().setStatus(HttpServletResponse.SC_ACCEPTED);
            then(response).should().setContentType("application/json");
            then(response).should().setCharacterEncoding("UTF-8");

            Person written = JsonUtil.getObjectMapper()
                    .readValue(responseBody.toString(), Person.class);
            assertThat(written).extracting(Person::name, Person::age)
                    .containsExactly("Bob", 25);
        }
    }

    // -------------------------------------------------------------------------
    // writeSuccess
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("writeSuccess()")
    class WriteSuccessTests {

        @Test
        @DisplayName("data overload should write 200 success response with data")
        void writeSuccess_withData_writesOkWithData() throws IOException {
            // Arrange
            Person payload = new Person("Carol", 28);

            // Act
            ServletUtil.writeSuccess(response, payload);

            // Assert
            then(response).should().setStatus(HttpServletResponse.SC_OK);
            SuccessResponse<Person> written = JsonUtil.getObjectMapper().readValue(
                    responseBody.toString(),
                    JsonUtil.getObjectMapper().getTypeFactory()
                            .constructParametricType(SuccessResponse.class, Person.class));
            assertThat(written.success).isTrue();
            assertThat(written.data).extracting(Person::name, Person::age)
                    .containsExactly("Carol", 28);
        }

        @Test
        @DisplayName("message overload should write 200 success response with message")
        void writeSuccess_withMessage_writesOkWithMessage() throws IOException {
            // Arrange
            String message = "Operation completed";

            // Act
            ServletUtil.writeSuccess(response, message);

            // Assert
            then(response).should().setStatus(HttpServletResponse.SC_OK);
            SuccessResponse<?> written = JsonUtil.getObjectMapper()
                    .readValue(responseBody.toString(), SuccessResponse.class);
            assertThat(written.success).isTrue();
            assertThat(written.message).isEqualTo(message);
        }
    }

    // -------------------------------------------------------------------------
    // writeCreated
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("writeCreated()")
    class WriteCreatedTests {

        @Test
        @DisplayName("should write 201 created response with data")
        void writeCreated_withData_writesCreatedWithData() throws IOException {
            // Arrange
            Person payload = new Person("Dave", 22);

            // Act
            ServletUtil.writeCreated(response, payload);

            // Assert
            then(response).should().setStatus(HttpServletResponse.SC_CREATED);
            SuccessResponse<Person> written = JsonUtil.getObjectMapper().readValue(
                    responseBody.toString(),
                    JsonUtil.getObjectMapper().getTypeFactory()
                            .constructParametricType(SuccessResponse.class, Person.class));
            assertThat(written.success).isTrue();
            assertThat(written.data).extracting(Person::name, Person::age)
                    .containsExactly("Dave", 22);
        }
    }

    // -------------------------------------------------------------------------
    // writeError
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("writeError()")
    class WriteErrorTests {

        @BeforeEach
        void setUpError() {
            given(request.getRequestURI()).willReturn("/api/test/path");
        }

        @Test
        @DisplayName("should write BaseException details with correct HTTP status")
        void writeError_baseException_writesErrorResponse() throws IOException {
            // Arrange
            ResourceNotFoundException ex = new ResourceNotFoundException("User not found");

            // Act
            ServletUtil.writeError(response, request, ex);

            // Assert
            then(response).should().setStatus(HttpServletResponse.SC_NOT_FOUND);
            ErrorResponse written = JsonUtil.getObjectMapper()
                    .readValue(responseBody.toString(), ErrorResponse.class);
            assertThat(written.getError()).isEqualTo(ex.getErrorCode());
            assertThat(written.getMessage()).isEqualTo("User not found");
            assertThat(written.getPath()).isEqualTo("/api/test/path");
            assertThat(written.isSuccess()).isFalse();
            assertThat(written.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should include field errors for ValidationException")
        void writeError_validationException_includesFieldErrors() throws IOException {
            // Arrange
            List<ErrorResponse.FieldError> fieldErrors = List.of(
                    new ErrorResponse.FieldError("email", "must not be blank")
            );
            ValidationException ex = new ValidationException("Validation failed", fieldErrors);

            // Act
            ServletUtil.writeError(response, request, ex);

            // Assert
            then(response).should().setStatus(HttpServletResponse.SC_BAD_REQUEST);
            ErrorResponse written = JsonUtil.getObjectMapper()
                    .readValue(responseBody.toString(), ErrorResponse.class);
            assertThat(written.getFieldErrors())
                    .hasSize(1)
                    .first()
                    .extracting(ErrorResponse.FieldError::getField, ErrorResponse.FieldError::getMessage)
                    .containsExactly("email", "must not be blank");
            assertThat(written.isSuccess()).isFalse();
            assertThat(written.getPath()).isEqualTo("/api/test/path");
        }

        @Test
        @DisplayName("should set Retry-After header for RateLimitExceededException")
        void writeError_rateLimitExceededException_setsRetryAfterHeader() throws IOException {
            // Arrange
            RateLimitExceededException ex = new RateLimitExceededException("Too many requests", 42L);

            // Act
            ServletUtil.writeError(response, request, ex);

            // Assert
            then(response).should().setStatus(429);
            ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);
            then(response).should().setHeader(org.mockito.ArgumentMatchers.eq("Retry-After"), headerValue.capture());
            assertThat(headerValue.getValue()).isEqualTo("42");

            ErrorResponse written = JsonUtil.getObjectMapper()
                    .readValue(responseBody.toString(), ErrorResponse.class);
            assertThat(written.getError()).isEqualTo(ex.getErrorCode());
            assertThat(written.getMessage()).isEqualTo("Too many requests");
            assertThat(written.isSuccess()).isFalse();
            assertThat(written.getPath()).isEqualTo("/api/test/path");
            assertThat(written.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should write generic internal error for non-BaseException")
        void writeError_genericException_writesInternalError() throws IOException {
            // Arrange
            RuntimeException ex = new RuntimeException("boom");

            // Act
            ServletUtil.writeError(response, request, ex);

            // Assert
            then(response).should().setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ErrorResponse written = JsonUtil.getObjectMapper()
                    .readValue(responseBody.toString(), ErrorResponse.class);
            assertThat(written.getError()).isEqualTo("INTERNAL_ERROR");
            assertThat(written.getMessage()).isEqualTo("An unexpected error occurred");
            assertThat(written.getPath()).isEqualTo("/api/test/path");
            assertThat(written.isSuccess()).isFalse();
            assertThat(written.getTimestamp()).isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // getClientIP
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getClientIP()")
    class GetClientIpTests {

        @Test
        @DisplayName("should return first IP from X-Forwarded-For when present")
        void getClientIp_xForwardedForPresent_returnsFirstIp() {
            // Arrange
            given(request.getHeader("X-Forwarded-For")).willReturn(" 203.0.113.1 , 198.51.100.2 ");

            // Act
            String result = ServletUtil.getClientIP(request);

            // Assert
            assertThat(result).isEqualTo("203.0.113.1");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("should fall back to X-Real-IP when X-Forwarded-For is absent or blank")
        void getClientIp_xForwardedForEmpty_fallsBackToXRealIp(String xForwardedFor) {
            // Arrange
            given(request.getHeader("X-Forwarded-For")).willReturn(xForwardedFor);
            given(request.getHeader("X-Real-IP")).willReturn("198.51.100.10");

            // Act
            String result = ServletUtil.getClientIP(request);

            // Assert
            assertThat(result).isEqualTo("198.51.100.10");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("should fall back to remote address when X-Real-IP is absent or blank")
        void getClientIp_xRealIpEmpty_fallsBackToRemoteAddr(String xRealIp) {
            // Arrange
            given(request.getHeader("X-Forwarded-For")).willReturn(null);
            given(request.getHeader("X-Real-IP")).willReturn(xRealIp);
            given(request.getRemoteAddr()).willReturn("192.168.1.5");

            // Act
            String result = ServletUtil.getClientIP(request);

            // Assert
            assertThat(result).isEqualTo("192.168.1.5");
        }

        @Test
        @DisplayName("should fall back to remote address when no proxy headers are present")
        void getClientIp_noProxyHeaders_fallsBackToRemoteAddr() {
            // Arrange
            given(request.getRemoteAddr()).willReturn("192.168.1.5");

            // Act
            String result = ServletUtil.getClientIP(request);

            // Assert
            assertThat(result).isEqualTo("192.168.1.5");
        }
    }

    // -------------------------------------------------------------------------
    // getUserAgent
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getUserAgent()")
    class GetUserAgentTests {

        @Test
        @DisplayName("should return User-Agent header when within length limit")
        void getUserAgent_withinLimit_returnsHeader() {
            // Arrange
            String agent = "Mozilla/5.0";
            given(request.getHeader("User-Agent")).willReturn(agent);

            // Act
            String result = ServletUtil.getUserAgent(request);

            // Assert
            assertThat(result).isEqualTo(agent);
        }

        @Test
        @DisplayName("should truncate User-Agent header to 500 characters when too long")
        void getUserAgent_tooLong_returnsTruncated() {
            // Arrange
            String longAgent = "A".repeat(600);
            given(request.getHeader("User-Agent")).willReturn(longAgent);

            // Act
            String result = ServletUtil.getUserAgent(request);

            // Assert
            assertThat(result).hasSize(500).isEqualTo("A".repeat(500));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("should return null when User-Agent header is absent or blank")
        void getUserAgent_blankHeader_returnsNull(String userAgent) {
            // Arrange
            given(request.getHeader("User-Agent")).willReturn(userAgent);

            // Act
            String result = ServletUtil.getUserAgent(request);

            // Assert
            assertThat(result).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // extractPathParam
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("extractPathParam()")
    class ExtractPathParamTests {

        @ParameterizedTest
        @CsvSource({
                "/api/users/123, /api/users/, 123",
                "/api/users/123/, /api/users/, 123",
                "/ctx/api/users/123, /api/users/, 123"
        })
        @DisplayName("should extract path parameter from request URI")
        void extractPathParam_fromRequestUri_returnsParam(String requestUri, String basePath, String expected) {
            // Arrange
            given(request.getRequestURI()).willReturn(requestUri);
            given(request.getContextPath()).willReturn("/ctx");

            // Act
            String result = ServletUtil.extractPathParam(request, basePath);

            // Assert
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("should prefer getPathInfo over getRequestURI when present")
        void extractPathParam_pathInfoPresent_usesPathInfo() {
            // Arrange
            given(request.getPathInfo()).willReturn("/users/42");
            lenient().when(request.getRequestURI()).thenReturn("/ctx/api/users/99");
            lenient().when(request.getContextPath()).thenReturn("/ctx");

            // Act
            String result = ServletUtil.extractPathParam(request, "/users/");

            // Assert
            assertThat(result).isEqualTo("42");
        }

        @Test
        @DisplayName("should extract parameter when context path does not prefix request URI")
        void extractPathParam_contextPathMismatch_extractsFromFullUri() {
            // Arrange
            given(request.getPathInfo()).willReturn(null);
            given(request.getRequestURI()).willReturn("/other/api/users/123");
            given(request.getContextPath()).willReturn("/ctx");

            // Act
            String result = ServletUtil.extractPathParam(request, "/other/api/users/");

            // Assert
            assertThat(result).isEqualTo("123");
        }

        @Test
        @DisplayName("should return null when path does not start with base path")
        void extractPathParam_mismatch_returnsNull() {
            // Arrange
            given(request.getPathInfo()).willReturn("/orders/1");

            // Act
            String result = ServletUtil.extractPathParam(request, "/users/");

            // Assert
            assertThat(result).isNull();
        }

        @ParameterizedTest
        @CsvSource({
                "/api/users/, /api/users/",
                "/api/users//, /api/users/"
        })
        @DisplayName("should return null when parameter segment is empty")
        void extractPathParam_emptyParam_returnsNull(String pathInfo, String basePath) {
            // Arrange
            given(request.getPathInfo()).willReturn(pathInfo);

            // Act
            String result = ServletUtil.extractPathParam(request, basePath);

            // Assert
            assertThat(result).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Helper types and stubs
    // -------------------------------------------------------------------------
    private record Person(String name, int age) {
    }

    private static class SuccessResponse<T> {
        public boolean success;
        public String message;
        public T data;
    }

    private static class ServletInputStreamStub extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        ServletInputStreamStub(String body) {
            this.delegate = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // no-op
        }
    }
}
