package unittests.util;

import com.airline.airlinebackend.util.JsonUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JsonUtil}.
 *
 * <p>These tests do <strong>not</strong> exist to verify Jackson internals.
 * They verify the three things this utility class owns:</p>
 * <ol>
 *   <li>The ObjectMapper configuration decisions made in the static block.</li>
 *   <li>The exception-translation contract (checked Jackson exceptions → RuntimeException).</li>
 *   <li>The public API surface of the helper methods.</li>
 * </ol>
 */
@DisplayName("JsonUtil")
public class JsonUtilTest {

    // ============================================================
    // Helper DTOs
    // ============================================================

    /**
     * Simple POJO carrying a {@link LocalDateTime} field.
     * Used to verify that the ObjectMapper has {@code JavaTimeModule} registered
     * and {@code WRITE_DATES_AS_TIMESTAMPS} disabled. Without those two settings,
     * serializing or deserializing a java.time type would fail or produce numeric timestamps.
     */
    static class SampleDTO {
        private String name;
        private LocalDateTime createdAt;

        public SampleDTO() {}

        public SampleDTO(String name, LocalDateTime createdAt) {
            this.name = name;
            this.createdAt = createdAt;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    /**
     * POJO whose getter throws an exception during serialization.
     * Used to force a {@link com.fasterxml.jackson.core.JsonProcessingException}
     * so we can verify the RuntimeException wrapping contract of {@link JsonUtil#toJson(Object)}.
     */
    static class BrokenSerializable {
        public String getValue() {
            throw new IllegalStateException("intentional serialization failure");
        }
    }

    // ============================================================
    // toJson(Object)
    // ============================================================

    @Nested
    @DisplayName("toJson(Object)")
    class ToJsonTests {

        @Test
        @DisplayName("serializes java.time fields to ISO-8601 strings")
        void serializesJavaTimeToIsoString() {
            // Arrange
            LocalDateTime fixedPoint = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
            SampleDTO dto = new SampleDTO("ticket", fixedPoint);

            // Act
            String json = JsonUtil.toJson(dto);

            // Assert
            // Guards the two configuration choices:
            // 1) JavaTimeModule is registered, 2) WRITE_DATES_AS_TIMESTAMPS is disabled.
            assertThat(json)
                    .contains("\"createdAt\":\"2024-01-15T10:30:00\"")
                    .contains("\"name\":\"ticket\"");
        }

        @Test
        @DisplayName("wraps JsonProcessingException in RuntimeException with expected message")
        void wrapsSerializationException() {
            // Arrange
            BrokenSerializable broken = new BrokenSerializable();

            // Act & Assert
            // Verifies the contract that callers never see Jackson's checked exception;
            // instead they receive a RuntimeException with a stable, human-readable message.
            assertThatThrownBy(() -> JsonUtil.toJson(broken))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to serialize object to json.")
                    .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }
    }

    // ============================================================
    // fromJson(String, Class)
    // ============================================================

    @Nested
    @DisplayName("fromJson(String, Class)")
    class FromJsonStringTests {

        @Test
        @DisplayName("deserializes java.time fields from ISO-8601 strings")
        void deserializesJavaTimeFromIsoString() {
            // Arrange
            String json = "{\"name\":\"ticket\",\"createdAt\":\"2024-01-15T10:30:00\"}";

            // Act
            SampleDTO result = JsonUtil.fromJson(json, SampleDTO.class);

            // Assert
            // Mirrors the serialization test: the same ObjectMapper config must also
            // deserialize java.time types back into the model object.
            assertThat(result.getName()).isEqualTo("ticket");
            assertThat(result.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("ignores unknown JSON properties instead of failing")
        void ignoresUnknownProperties() {
            // Arrange
            // "unknownKey" is not declared on SampleDTO. With FAIL_ON_UNKNOWN_PROPERTIES
            // disabled this must succeed; with it enabled Jackson would throw.
            String jsonWithExtraKey = "{\"name\":\"ticket\",\"createdAt\":\"2024-01-15T10:30:00\",\"unknownKey\":\"ignored\"}";

            // Act
            SampleDTO result = JsonUtil.fromJson(jsonWithExtraKey, SampleDTO.class);

            // Assert
            assertThat(result.getName()).isEqualTo("ticket");
            assertThat(result.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("wraps JsonProcessingException in RuntimeException with expected message")
        void wrapsDeserializationException() {
            // Arrange
            String invalidJson = "{ invalid json";

            // Act & Assert
            assertThatThrownBy(() -> JsonUtil.fromJson(invalidJson, SampleDTO.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to deserialize json to object.")
                    .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }
    }

    // ============================================================
    // fromJson(InputStream, Class)
    // ============================================================

    @Nested
    @DisplayName("fromJson(InputStream, Class)")
    class FromJsonInputStreamTests {

        @Test
        @DisplayName("deserializes valid JSON from an InputStream")
        void deserializesFromInputStream() {
            // Arrange
            String json = "{\"name\":\"stream-ticket\",\"createdAt\":\"2024-06-01T12:00:00\"}";
            InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

            // Act
            SampleDTO result = JsonUtil.fromJson(inputStream, SampleDTO.class);

            // Assert
            assertThat(result.getName()).isEqualTo("stream-ticket");
            assertThat(result.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 6, 1, 12, 0, 0));
        }

        @Test
        @DisplayName("wraps IOException in RuntimeException with expected message")
        void wrapsInputStreamDeserializationException() {
            // Arrange
            String invalidJson = "{ broken";
            InputStream inputStream = new ByteArrayInputStream(invalidJson.getBytes(StandardCharsets.UTF_8));

            // Act & Assert
            // The InputStream overload can throw IOException (a superclass of JsonProcessingException),
            // so this test guards that the broader checked exception is also translated.
            assertThatThrownBy(() -> JsonUtil.fromJson(inputStream, SampleDTO.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to deserialize json from inputStream.")
                    .hasCauseInstanceOf(java.io.IOException.class);
        }
    }

    // ============================================================
    // getObjectMapper()
    // ============================================================

    @Nested
    @DisplayName("getObjectMapper()")
    class GetObjectMapperTests {

        @Test
        @DisplayName("returns the same configured ObjectMapper singleton")
        void returnsConfiguredSingleton() {
            // Act
            ObjectMapper first = JsonUtil.getObjectMapper();
            ObjectMapper second = JsonUtil.getObjectMapper();

            // Assert
            // Callers (e.g. servlets that write JSON directly to the response) rely on
            // receiving the same pre-configured instance every time.
            assertThat(first).isSameAs(second);
            assertThat(first.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
            assertThat(first.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        }
    }
}
