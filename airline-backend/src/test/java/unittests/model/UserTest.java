package unittests.model;

import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.emums.Role;
import com.airline.airlinebackend.model.emums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests for the user model class")
public class UserTest {
    @Test
    @DisplayName("Testing creation of User with builder")
    public void testBuilderCreation() {
        UUID userId = UUID.randomUUID();
        String name = "John Doe";
        String password = "Doe";
        String email = "john.doe@example.com";
        Role role = Role.ADMIN;
        UserStatus status = UserStatus.ACTIVE;
        boolean passwordChanged = true;
        boolean firstLogin = true;
        Instant lastLogin = Instant.now().plus(Duration.ofHours(1));
        Instant created = Instant.now();
        Instant updated = Instant.now().plus(Duration.ofMinutes(1));

        User user = User.builder()
                .id(userId)
                .name(name)
                .passwordHash(password)
                .email(email)
                .role(role)
                .status(status)
                .passwordChangeRequired(passwordChanged)
                .firstLoginCompleted(firstLogin)
                .lastLoginAt(lastLogin)
                .createdAt(created)
                .updatedAt(updated)
                .build();

        assertNotNull(user);
        assertEquals(userId, user.getId());
        assertEquals(name, user.getName());
        assertEquals(password, user.getPasswordHash());
        assertEquals(email, user.getEmail());
        assertEquals(role, user.getRole());
        assertEquals(status, user.getStatus());
        assertEquals(created, user.getCreatedAt());
        assertEquals(updated, user.getUpdatedAt());
        assertEquals(lastLogin, user.getLastLoginAt());
        assertEquals(passwordChanged,user.isPasswordChangeRequired());
        assertEquals(firstLogin,user.isFirstLoginCompleted());
    }

    @Test
    @DisplayName("Test equals and hashcode implementation by id")
    public void testEqualsAndHashcode() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        User user1 = User.builder().id(userId1).build();
        User user2 = User.builder().id(userId2).build();

        assertNotNull(user1);
        assertNotNull(user2);
        assertNotEquals(user1.hashCode(), user2.hashCode());
        assertNotEquals(user1,user2);

        //creating users with equivalent ids
        User user11 = User.builder().id(userId1).build();
        User user12 = User.builder().id(userId1).build();

        assertNotNull(user11);
        assertNotNull(user12);
        assertEquals(user11.hashCode(),user12.hashCode());
        assertEquals(user11,user12);
    }

    @Test
    @DisplayName("Test isLogin returns correct values")
    public void testIsLogin() {
        User user = User.builder().status(UserStatus.ACTIVE).build();
        assertNotNull(user);
        assertTrue(user.isLogin());

        user.setStatus(UserStatus.PENDING_EMAIL_CONFIRMATION);
        assertFalse(user.isLogin());

        user.setStatus(UserStatus.BLOCKED);
        assertFalse(user.isLogin());

        user.setStatus(UserStatus.PENDING_APPROVAL);
        assertFalse(user.isLogin());
    }

    @Test
    @DisplayName("Test isAdmin returns correct values")
    public void testIsAdmin() {
        User user = User.builder().role(Role.ADMIN).build();

        assertNotNull(user);
        assertTrue(user.isAdmin());

        user.setRole(Role.CUSTOMER_MANAGER);
        assertFalse(user.isAdmin());

        user.setRole(Role.FLIGHT_MANAGER);
        assertFalse(user.isAdmin());
    }
}
