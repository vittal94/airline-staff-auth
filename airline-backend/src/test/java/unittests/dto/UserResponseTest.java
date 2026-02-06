package unittests.dto;

import com.airline.airlinebackend.dto.response.UserResponse;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.emums.Role;
import com.airline.airlinebackend.model.emums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("UserResponse test only for fromUser method")
public class UserResponseTest {
    private static final Logger log = LoggerFactory.getLogger(UserResponseTest.class);
    @Test
    public void fromUserTest() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Test")
                .email("com@mail.ru")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .passwordChangeRequired(true)
                .firstLoginCompleted(true)
                .createdAt(Instant.now())
                .lastLoginAt(Instant.now().plus(Duration.ofHours(1)))
                .build();
        UserResponse userResponse = UserResponse.fromUser(user);

        assertNotNull(user);
        assertNotNull(userResponse);

        assertEquals(user.getId(), userResponse.getId());
        assertEquals(user.getName(), userResponse.getName());
        assertEquals(user.getEmail(), userResponse.getEmail());
        assertEquals(user.getRole().toString(), userResponse.getRole());
        assertEquals(user.getStatus().toString(), userResponse.getStatus());
        assertEquals(user.isPasswordChangeRequired(), userResponse.isPasswordChangeRequired());
        assertEquals(user.isFirstLoginCompleted(), userResponse.isFirstLoginCompleted());
        assertEquals(user.getCreatedAt(), userResponse.getCreatedAt());
        assertEquals(user.getLastLoginAt(), userResponse.getLastLoginAt());

        Set<String> userPermissions = user.getRole().getPermissions().stream()
                        .map(Enum::toString)
                        .collect(Collectors.toSet());

        assertNotNull(userResponse.getPermissions());
        assertEquals(userPermissions.size(), userResponse.getPermissions().size());
        assertEquals(userPermissions, userResponse.getPermissions());

        log.info("UserResponse getRole: {}, getStatus: {}", userResponse.getRole(), user.getStatus());
    }

}
