package unittests.servlet;

import com.airline.airlinebackend.servlet.HomeServlet;
import jakarta.servlet.http.HttpServletRequest;
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

import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for {@link HomeServlet}.
 */
@DisplayName("HomeServlet")
@ExtendWith(MockitoExtension.class)
class HomeServletTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private PrintWriter writer;

    @Captor
    private ArgumentCaptor<String> stringCaptor;

    private HomeServlet homeServlet;

    @BeforeEach
    void setUp() {
        homeServlet = new HomeServlet();
    }

    // doGet() has protected access from this package; service() is the public
    // container entry point and dispatches to doGet() for GET requests.
    private void doGet() throws Exception {
        given(request.getMethod()).willReturn("GET");
        homeServlet.service(request, response);
    }

    private void verifySuccessJson() throws Exception {
        then(response).should().setStatus(HttpServletResponse.SC_OK);
        then(response).should().setContentType("application/json");
        then(response).should().setCharacterEncoding("UTF-8");
        then(writer).should().write(stringCaptor.capture());
        assertThat(stringCaptor.getValue()).contains("\"success\":true");
    }

    @Nested
    @DisplayName("doGet() - health endpoint")
    class HealthEndpointTests {

        @ParameterizedTest(name = "URI: {0}")
        @ValueSource(strings = {"/api/health"})
        @DisplayName("should respond 200 with status UP payload for the health endpoint")
        void doGet_healthEndpoint_writesStatusUpJson(String uri) throws Exception {
            // Arrange
            given(request.getRequestURI()).willReturn(uri);
            given(response.getWriter()).willReturn(writer);

            // Act
            doGet();

            // Assert
            verifySuccessJson();
            assertThat(stringCaptor.getValue())
                    .contains("\"status\":\"UP\"")
                    .contains("\"timestamp\"");
        }
    }

    @Nested
    @DisplayName("doGet() - home endpoint")
    class HomeEndpointTests {

        @Test
        @DisplayName("should respond 200 with the welcome payload for the home endpoint")
        void doGet_homeEndpoint_writesWelcomeJson() throws Exception {
            // Arrange
            given(request.getRequestURI()).willReturn("/api/home");
            given(response.getWriter()).willReturn(writer);

            // Act
            doGet();

            // Assert
            verifySuccessJson();
            assertThat(stringCaptor.getValue())
                    .contains("\"message\":\"Welcome to airline staff system\"")
                    .contains("\"version\":\"1.0.0\"");
        }
    }
}
