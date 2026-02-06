package unittests.dto;

import com.airline.airlinebackend.dto.response.CabinetResponse;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.emums.Role;
import com.airline.airlinebackend.model.emums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CabinetResponse Tests")
public class CabinetResponseTest {

    @Nested
    @DisplayName("fromUser Method Tests")
    class FromUserTests {

        @Test
        @DisplayName("Should correctly map ADMIN user to CabinetResponse")
        public void testFromUserWithAdmin() {
            // Arrange
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .name("Admin User")
                    .email("admin@airline.com")
                    .role(Role.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .passwordChangeRequired(false)
                    .firstLoginCompleted(true)
                    .createdAt(Instant.now())
                    .build();

            // Act
            CabinetResponse response = CabinetResponse.fromUser(user);

            // Assert
            assertNotNull(response);
            assertEquals(user.getId(), response.getId());
            assertEquals(user.getName(), response.getName());
            assertEquals(user.getEmail(), response.getEmail());
            assertEquals(user.getRole().toString(), response.getRole());
            assertEquals(user.isPasswordChangeRequired(), response.isPasswordChangeRequired());
            assertEquals(user.isFirstLoginCompleted(), response.isFirstLoginComplete());

            // Verify permissions
            Set<String> expectedPermissions = user.getRole().getPermissions().stream()
                    .map(Enum::toString)
                    .collect(Collectors.toSet());
            assertNotNull(response.getPermissions());
            assertEquals(expectedPermissions.size(), response.getPermissions().size());
            assertEquals(expectedPermissions, response.getPermissions());

            // ADMIN should have 5 permissions
            assertEquals(5, response.getPermissions().size());
            assertTrue(response.getPermissions().contains("ADMIN_DASHBOARD"));
            assertTrue(response.getPermissions().contains("FLIGHT_MANAGER_DASHBOARD"));
            assertTrue(response.getPermissions().contains("CUSTOMER_MANAGER_DASHBOARD"));
            assertTrue(response.getPermissions().contains("MANAGE_USERS"));
            assertTrue(response.getPermissions().contains("VIEW_ALL_USERS"));
        }

        @Test
        @DisplayName("Should correctly map FLIGHT_MANAGER user to CabinetResponse")
        public void testFromUserWithFlightManager() {
            // Arrange
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .name("Flight Manager")
                    .email("flightmanager@airline.com")
                    .role(Role.FLIGHT_MANAGER)
                    .status(UserStatus.ACTIVE)
                    .passwordChangeRequired(true)
                    .firstLoginCompleted(false)
                    .createdAt(Instant.now())
                    .build();

            // Act
            CabinetResponse response = CabinetResponse.fromUser(user);

            // Assert
            assertNotNull(response);
            assertEquals(user.getId(), response.getId());
            assertEquals(user.getName(), response.getName());
            assertEquals(user.getEmail(), response.getEmail());
            assertEquals("FLIGHT_MANAGER", response.getRole());
            assertTrue(response.isPasswordChangeRequired());
            assertFalse(response.isFirstLoginComplete());

            // Verify permissions
            Set<String> expectedPermissions = user.getRole().getPermissions().stream()
                    .map(Enum::toString)
                    .collect(Collectors.toSet());
            assertEquals(expectedPermissions, response.getPermissions());

            // FLIGHT_MANAGER should have 2 permissions
            assertEquals(2, response.getPermissions().size());
            assertTrue(response.getPermissions().contains("FLIGHT_MANAGER_DASHBOARD"));
            assertTrue(response.getPermissions().contains("CUSTOMER_MANAGER_DASHBOARD"));
        }

        @Test
        @DisplayName("Should correctly map CUSTOMER_MANAGER user to CabinetResponse")
        public void testFromUserWithCustomerManager() {
            // Arrange
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .name("Customer Manager")
                    .email("customermanager@airline.com")
                    .role(Role.CUSTOMER_MANAGER)
                    .status(UserStatus.ACTIVE)
                    .passwordChangeRequired(false)
                    .firstLoginCompleted(true)
                    .createdAt(Instant.now())
                    .build();

            // Act
            CabinetResponse response = CabinetResponse.fromUser(user);

            // Assert
            assertNotNull(response);
            assertEquals(user.getId(), response.getId());
            assertEquals(user.getName(), response.getName());
            assertEquals(user.getEmail(), response.getEmail());
            assertEquals("CUSTOMER_MANAGER", response.getRole());
            assertFalse(response.isPasswordChangeRequired());
            assertTrue(response.isFirstLoginComplete());

            // Verify permissions
            Set<String> expectedPermissions = user.getRole().getPermissions().stream()
                    .map(Enum::toString)
                    .collect(Collectors.toSet());
            assertEquals(expectedPermissions, response.getPermissions());

            // CUSTOMER_MANAGER should have 1 permission
            assertEquals(1, response.getPermissions().size());
            assertTrue(response.getPermissions().contains("CUSTOMER_MANAGER_DASHBOARD"));
        }

        @Test
        @DisplayName("Should handle user with passwordChangeRequired=true and firstLoginComplete=false")
        public void testFromUserWithPasswordChangeRequired() {
            // Arrange
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .name("New User")
                    .email("newuser@airline.com")
                    .role(Role.CUSTOMER_MANAGER)
                    .status(UserStatus.PENDING_EMAIL_CONFIRMATION)
                    .passwordChangeRequired(true)
                    .firstLoginCompleted(false)
                    .createdAt(Instant.now())
                    .build();

            // Act
            CabinetResponse response = CabinetResponse.fromUser(user);

            // Assert
            assertNotNull(response);
            assertTrue(response.isPasswordChangeRequired());
            assertFalse(response.isFirstLoginComplete());
        }

        @Test
        @DisplayName("Should preserve all user fields during mapping")
        public void testFromUserFieldMapping() {
            // Arrange
            UUID testId = UUID.randomUUID();
            String testName = "Test User Name";
            String testEmail = "test@airline.com";

            User user = User.builder()
                    .id(testId)
                    .name(testName)
                    .email(testEmail)
                    .role(Role.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .passwordChangeRequired(false)
                    .firstLoginCompleted(true)
                    .createdAt(Instant.now())
                    .build();

            // Act
            CabinetResponse response = CabinetResponse.fromUser(user);

            // Assert - verify exact field values
            assertEquals(testId, response.getId());
            assertEquals(testName, response.getName());
            assertEquals(testEmail, response.getEmail());
        }
    }
}
