package unittests.model;


import com.airline.airlinebackend.model.emums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserStatus tests")
public class UserStatusTest {
    @Nested
    @DisplayName("CanLogin tests")
    public class CanLoginTests {
        @Test
        @DisplayName("ACTIVE status can login")
        public void testActiveCanLogin() {
            assertTrue(UserStatus.ACTIVE.canLogin());
        }

        @Test
        @DisplayName("PENDING_EMAIL_CONFIRMATION status can login")
        public void testPendingEmailConfirmationCanLogin() {
            assertFalse(UserStatus.PENDING_EMAIL_CONFIRMATION.canLogin());
        }

        @Test
        @DisplayName("PENDING_APPROVAL status can login")
        public void testPendingApprovalCanLogin() {
            assertFalse(UserStatus.PENDING_APPROVAL.canLogin());
        }

        @Test
        @DisplayName("BLOCKED status can login")
        public void testBlockedCanLogin() {
            assertFalse(UserStatus.BLOCKED.canLogin());
        }
    }

    @Nested
    @DisplayName("Get value tests")
    public class GetValueTests {
        @Test
        @DisplayName("ACTIVE status getValue return correct value")
        public void testActiveGetValue() {
            assertEquals("ACTIVE",UserStatus.ACTIVE.getValue());
        }

        @Test
        @DisplayName("PENDING_EMAIL_CONFIRMATION status getValue returns correct value")
        public void testPendingEmailConfirmationGetValue() {
            assertEquals("PENDING_EMAIL_CONFIRMATION",UserStatus.PENDING_EMAIL_CONFIRMATION.getValue());
        }

        @Test
        @DisplayName("BLOCKED status getValue return correct value")
        public void testBlockedGetValue() {
            assertEquals("BLOCKED",UserStatus.BLOCKED.getValue());
        }

        @Test
        @DisplayName("PENDING_APPROVAL status getValue returns correct value")
        public void testPendingApprovalGetValue() {
            assertEquals("PENDING_APPROVAL",UserStatus.PENDING_APPROVAL.getValue());
        }
    }

    @Nested
    @DisplayName("fromValue tests")
    public class FromValueTests {
        @Test
        @DisplayName("fromValue with 'ACTIVE' returns ACTIVE status")
        public void testFromValueWithActiveStatus() {
            UserStatus status = UserStatus.fromValue("ACTIVE");
            assertSame(UserStatus.ACTIVE, status);
            assertEquals(UserStatus.ACTIVE, status);
        }

        @Test
        @DisplayName("fromValue with 'active' (lowercase) returns ACTIVE status")
        public void testFromValueWithLowercaseActiveStatus() {
            UserStatus status = UserStatus.fromValue("active");
            assertSame(UserStatus.ACTIVE, status);
        }

        @Test
        @DisplayName("fromValue with 'AcTiVe' (mixed case) return ACTIVE status ")
        public void testFromValueWithMixedCaseActiveStatus() {
            UserStatus status = UserStatus.fromValue("AcTiVe");
            assertSame(UserStatus.ACTIVE, status);
        }

        @Test
        @DisplayName("fromValue with 'PENDING_EMAIL_CONFIRMATION' returns correct status")
        public void testFromValuePendingEmailConfirmation() {
            UserStatus status = UserStatus.fromValue("PENDING_EMAIL_CONFIRMATION");
            assertSame(UserStatus.PENDING_EMAIL_CONFIRMATION, status);
            assertEquals(UserStatus.PENDING_EMAIL_CONFIRMATION, status);
        }

        @Test
        @DisplayName("fromValue with 'PENDING_APPROVAL' returns correct status")
        public void testFromValuePendingApproval() {
            UserStatus status = UserStatus.fromValue("PENDING_APPROVAL");
            assertSame(UserStatus.PENDING_APPROVAL, status);
            assertEquals(UserStatus.PENDING_APPROVAL, status);
        }

        @Test
        @DisplayName("fromValue with 'BLOCKED' returns correct status")
        public void testFromValueBlocked() {
            UserStatus status = UserStatus.fromValue("BLOCKED");
            assertSame(UserStatus.BLOCKED, status);
            assertEquals(UserStatus.BLOCKED, status);
        }

        @ParameterizedTest
        @ValueSource(strings = {"INVALID", "null", "PENDING", "CONFIRMED", "", "  "})
        @DisplayName("fromValue with invalid values throws IllegalArgException")
        public void testFromValueWithInvalidValues(String value) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> UserStatus.fromValue(value)
            );
            assertEquals(exception.getMessage(), "Unknown user status:" + value);
        }

        @Test
        @DisplayName("fromValue with null throws IllegalArgException")
        public void testFromValueWithNullValues() {
            assertThrows(IllegalArgumentException.class, () -> UserStatus.fromValue(null));
        }
    }

    @Nested
    @DisplayName("Enum properties tests")
    public class EnumPropertiesTests {
        @Test
        @DisplayName("All enum values exist")
        public void testAllEnumValues() {
            UserStatus[] values = UserStatus.values();
            assertEquals(4, values.length);

            assertNotNull(UserStatus.ACTIVE);
            assertNotNull(UserStatus.PENDING_EMAIL_CONFIRMATION);
            assertNotNull(UserStatus.BLOCKED);
            assertNotNull(UserStatus.PENDING_APPROVAL);
        }

        @ParameterizedTest
        @EnumSource(UserStatus.class)
        @DisplayName("All enum values have non-null value strings")
        public void testAllEnumValuesWithNonNullValueStrings(UserStatus status) {
            assertNotNull(status.getValue());
            assertFalse(status.getValue().isEmpty());
        }

        @Test
        @DisplayName("Enum values in correct order")
        public void testEnumValuesInCorrectOrder() {
            UserStatus[] values = UserStatus.values();
            assertEquals(UserStatus.PENDING_EMAIL_CONFIRMATION, values[0]);
            assertEquals(UserStatus.PENDING_APPROVAL, values[1]);
            assertEquals(UserStatus.ACTIVE, values[2]);
            assertEquals(UserStatus.BLOCKED, values[3]);
        }
    }

}
