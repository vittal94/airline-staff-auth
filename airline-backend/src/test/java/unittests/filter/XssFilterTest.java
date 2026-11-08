package unittests.filter;

import com.airline.airlinebackend.filter.XssFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for {@link XssFilter}.
 *
 * The filter's contract is what downstream servlets observe: every parameter
 * and most headers arrive sanitized, while structural headers pass through
 * untouched. The wrapper is a private inner class, so each test drives the
 * filter through doFilter() and captures the wrapped request the chain
 * receives — asserting on the request a servlet would actually see.
 */
@DisplayName("XssFilter")
@ExtendWith(MockitoExtension.class)
class XssFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Captor
    private ArgumentCaptor<ServletRequest> wrappedRequestCaptor;

    private XssFilter xssFilter;

    @BeforeEach
    void setUp() {
        xssFilter = new XssFilter();
    }

    /**
     * Runs the filter and returns the wrapped request the chain received.
     */
    private HttpServletRequestWrapper filterAndCapture() throws Exception {
        // Act
        xssFilter.doFilter(request, response, chain);

        // Capture the wrapped request passed downstream
        then(chain).should().doFilter(wrappedRequestCaptor.capture(), same(response));
        assertThat(wrappedRequestCaptor.getValue()).isInstanceOf(HttpServletRequestWrapper.class);
        return (HttpServletRequestWrapper) wrappedRequestCaptor.getValue();
    }

    @Nested
    @DisplayName("doFilter()")
    class DoFilterTests {

        @Test
        @DisplayName("should wrap the request, continue the chain, and pass the response through untouched")
        void doFilter_anyRequest_wrapsRequestAndContinuesChain() throws Exception {
            // Arrange
            given(request.getParameter("q")).willReturn("search term");

            // Act
            HttpServletRequestWrapper wrapped = filterAndCapture();

            // Assert
            assertThat(wrapped.getRequest()).isSameAs(request);
            assertThat(wrapped.getParameter("q")).isEqualTo("search term");
        }
    }

    @Nested
    @DisplayName("getParameter()")
    class GetParameterTests {

        @Test
        @DisplayName("should strip a full script payload, leaving an empty value")
        void getParameter_scriptPayload_returnsEmptyString() throws Exception {
            // Arrange
            given(request.getParameter("q")).willReturn("<script>alert('xss')</script>");

            // Act
            String value = filterAndCapture().getParameter("q");

            // Assert
            assertThat(value).isEmpty();
        }

        @Test
        @DisplayName("should remove inline tags from the value but keep the surrounding text")
        void getParameter_inlineTagPayload_returnsTagFreeText() throws Exception {
            // Arrange
            given(request.getParameter("name")).willReturn("John <b onclick=\"steal()\">Doe</b>");

            // Act
            String value = filterAndCapture().getParameter("name");

            // Assert
            assertThat(value).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("should return plain text unchanged")
        void getParameter_plainText_returnsSameValue() throws Exception {
            // Arrange
            given(request.getParameter("q")).willReturn("flight AA-100");

            // Act
            String value = filterAndCapture().getParameter("q");

            // Assert
            assertThat(value).isEqualTo("flight AA-100");
        }

        @Test
        @DisplayName("should return null when the parameter is absent instead of failing")
        void getParameter_missingParameter_returnsNull() throws Exception {
            // Act
            String value = filterAndCapture().getParameter("missing");

            // Assert
            assertThat(value).isNull();
        }
    }

    @Nested
    @DisplayName("getParameterValues()")
    class GetParameterValuesTests {

        @Test
        @DisplayName("should sanitize every value in the array")
        void getParameterValues_mixedPayloads_sanitizesAllValues() throws Exception {
            // Arrange
            given(request.getParameterValues("destinations"))
                    .willReturn(new String[]{"<b>Paris</b>", "Tokyo", "<img src=x onerror=alert(1)>"});

            // Act
            String[] values = filterAndCapture().getParameterValues("destinations");

            // Assert
            assertThat(values).containsExactly("Paris", "Tokyo", "");
        }

        @Test
        @DisplayName("should return null when the parameter is absent instead of an empty array")
        void getParameterValues_missingParameter_returnsNull() throws Exception {
            // Act
            String[] values = filterAndCapture().getParameterValues("missing");

            // Assert
            assertThat(values).isNull();
        }
    }

    @Nested
    @DisplayName("getParameterMap()")
    class GetParameterMapTests {

        @Test
        @DisplayName("should sanitize every entry and return an unmodifiable copy")
        void getParameterMap_xssPayloads_sanitizesAllEntries() throws Exception {
            // Arrange
            Map<String, String[]> original = new HashMap<>();
            original.put("name", new String[]{"Alice <b>admin</b>"});
            original.put("comment", new String[]{"<script>alert('xss')</script>", "plain"});
            given(request.getParameterMap()).willReturn(original);

            // Act
            Map<String, String[]> map = filterAndCapture().getParameterMap();

            // Assert
            assertThat(map.get("name")).containsExactly("Alice admin");
            assertThat(map.get("comment")).containsExactly("", "plain");

            // The copy handed downstream must be read-only
            assertThatThrownBy(() -> map.put("extra", new String[]{"value"}))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should not mutate the underlying request's parameter arrays")
        void getParameterMap_sanitization_doesNotTouchOriginalArrays() throws Exception {
            // Arrange
            Map<String, String[]> original = new HashMap<>();
            original.put("name", new String[]{"Alice <b>admin</b>"});
            given(request.getParameterMap()).willReturn(original);

            // Act
            filterAndCapture().getParameterMap();

            // Assert
            assertThat(original.get("name")).containsExactly("Alice <b>admin</b>");
        }

        @Test
        @DisplayName("should return an empty map when the request has no parameters")
        void getParameterMap_noParameters_returnsEmptyMap() throws Exception {
            // Arrange
            given(request.getParameterMap()).willReturn(Map.of());

            // Act
            Map<String, String[]> map = filterAndCapture().getParameterMap();

            // Assert
            assertThat(map).isEmpty();
        }
    }

    @Nested
    @DisplayName("getHeader()")
    class GetHeaderTests {

        @Test
        @DisplayName("should sanitize a spoofable non-exempt header")
        void getHeader_nonExemptHeader_sanitizesValue() throws Exception {
            // Arrange
            given(request.getHeader("Referer")).willReturn("<script>alert(document.cookie)</script>");

            // Act
            String value = filterAndCapture().getHeader("Referer");

            // Assert
            assertThat(value).isEmpty();
        }

        @ParameterizedTest(name = "header: {0}")
        @ValueSource(strings = {
                "Content-Type", "content-type", "CONTENT-TYPE",
                "Authorization", "authorization",
                "X-Forwarded-For", "x-custom-header"})
        @DisplayName("should pass structural and custom headers through unsanitized")
        void getHeader_exemptHeaders_passesValueThrough(String header) throws Exception {
            // Arrange — even a raw payload must survive for exempt headers:
            // sanitizing Content-Type would break multipart boundaries and
            // sanitizing Authorization would corrupt bearer tokens
            given(request.getHeader(header)).willReturn("<script>alert(1)</script>");

            // Act
            String value = filterAndCapture().getHeader(header);

            // Assert
            assertThat(value).isEqualTo("<script>alert(1)</script>");
        }

        @Test
        @DisplayName("should return null for an absent non-exempt header instead of failing")
        void getHeader_missingHeader_returnsNull() throws Exception {
            // Act
            String value = filterAndCapture().getHeader("Accept");

            // Assert
            assertThat(value).isNull();
        }
    }
}