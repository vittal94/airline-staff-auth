package unittests.security;

import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.exception.AccountBlockedException;
import com.airline.airlinebackend.exception.AccountNotApprovedException;
import com.airline.airlinebackend.exception.AuthenticationException;
import com.airline.airlinebackend.exception.EmailNotConfirmedException;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.enums.Role;
import com.airline.airlinebackend.model.enums.UserStatus;
import com.airline.airlinebackend.security.DatabaseAuthenticator;
import com.airline.airlinebackend.security.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.profile.CommonProfile;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link DatabaseAuthenticator}.
 * <p>
 * These tests verify credential validation, status checks, and profile population
 * using a mocked {@link UserDAO}. A real {@link UsernamePasswordCredentials} instance
 * is used so the populated {@link CommonProfile} can be asserted directly.
 */
@DisplayName("DatabaseAuthenticator")
@ExtendWith(MockitoExtension.class)
public class DatabaseAuthenticatorTest {

    private static final String EMAIL = "email@gmail.com";
    private static final String PASSWORD = "Password";
    private static final String NAME = "name";

    private DatabaseAuthenticator authenticator;

    @Mock
    private UserDAO userDAO;

    @BeforeEach
    void setUp() {
        authenticator = new DatabaseAuthenticator(userDAO);
    }

    private User buildUser(
            UUID id,
            String name,
            String passwordHash,
            String email,
            Role role,
            UserStatus status,
            boolean passwordChangeRequired,
            boolean firstLoginCompleted
    ) {
        return User.builder()
                .id(id)
                .passwordHash(passwordHash)
                .email(email)
                .role(role)
                .name(name)
                .status(status)
                .lastLoginAt(Instant.now())
                .createdAt(Instant.now())
                .firstLoginCompleted(firstLoginCompleted)
                .updatedAt(Instant.now())
                .passwordChangeRequired(passwordChangeRequired)
                .build();
    }

    @Test
    @DisplayName("should return Credentials with fully populated profile for active user")
    void validate_activeUser_returnsCredentialsWithProfile() {
        // Arrange
        String passwordHash = PasswordEncoder.encode(PASSWORD);
        UUID userId = UUID.randomUUID();
        User actualUser = buildUser(
                userId,
                NAME,
                passwordHash,
                EMAIL,
                Role.FLIGHT_MANAGER,
                UserStatus.ACTIVE,
                true,
                true
        );
        given(userDAO.findByEmail(EMAIL)).willReturn(Optional.of(actualUser));

        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(EMAIL, PASSWORD);

        // Act
        Optional<Credentials> result = authenticator.validate(null, credentials);

        // Assert
        assertThat(result).isPresent().containsSame(credentials);

        CommonProfile profile = (CommonProfile) credentials.getUserProfile();
        assertThat(profile).isNotNull();
        assertThat(profile.getId()).isEqualTo(userId.toString());
        assertThat(profile.getAttribute("email")).isEqualTo(EMAIL);
        assertThat(profile.getAttribute("name")).isEqualTo(NAME);
        assertThat(profile.getAttribute("role")).isEqualTo(Role.FLIGHT_MANAGER.getValue());
        assertThat(profile.getAttribute("status")).isEqualTo(UserStatus.ACTIVE.getValue());
        assertThat(profile.getAttribute("passwordChangeRequired")).isEqualTo(true);
        assertThat(profile.getAttribute("firstLoginCompleted")).isEqualTo(true);
        assertThat(profile.getRoles()).containsExactly(Role.FLIGHT_MANAGER.getValue());

        @SuppressWarnings("unchecked")
        Set<String> permissions = (Set<String>) profile.getAttribute("permissions");
        assertThat(permissions)
                .containsExactlyInAnyOrderElementsOf(
                        Role.FLIGHT_MANAGER.getPermissions().stream().map(Enum::name).toList()
                );
    }

    @Test
    @DisplayName("should throw CredentialsException when credentials type is not UsernamePasswordCredentials")
    void validate_invalidCredentialsType_throwsCredentialsException() {
        // Arrange
        Credentials unsupportedCredentials = new Credentials() {};

        // Act & Assert
        assertThatThrownBy(() -> authenticator.validate(null, unsupportedCredentials))
                .isInstanceOf(CredentialsException.class)
                .hasMessageContaining("Invalid credentials type");
    }

    @Test
    @DisplayName("should throw AuthenticationException when user is not found")
    void validate_unknownUser_throwsAuthenticationException() {
        // Arrange
        given(userDAO.findByEmail(EMAIL)).willReturn(Optional.empty());
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(EMAIL, PASSWORD);

        // Act & Assert
        assertThatThrownBy(() -> authenticator.validate(null, credentials))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("should throw AuthenticationException when password does not match")
    void validate_wrongPassword_throwsAuthenticationException() {
        // Arrange
        User actualUser = buildUser(
                UUID.randomUUID(),
                NAME,
                PasswordEncoder.encode("differentPassword"),
                EMAIL,
                Role.FLIGHT_MANAGER,
                UserStatus.ACTIVE,
                false,
                false
        );
        given(userDAO.findByEmail(EMAIL)).willReturn(Optional.of(actualUser));
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(EMAIL, PASSWORD);

        // Act & Assert
        assertThatThrownBy(() -> authenticator.validate(null, credentials))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Invalid password");
    }

    @Test
    @DisplayName("should throw EmailNotConfirmedException when user status is PENDING_EMAIL_CONFIRMATION")
    void validate_pendingEmailConfirmation_throwsEmailNotConfirmedException() {
        // Arrange
        User actualUser = buildUser(
                UUID.randomUUID(),
                NAME,
                PasswordEncoder.encode(PASSWORD),
                EMAIL,
                Role.FLIGHT_MANAGER,
                UserStatus.PENDING_EMAIL_CONFIRMATION,
                false,
                false
        );
        given(userDAO.findByEmail(EMAIL)).willReturn(Optional.of(actualUser));
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(EMAIL, PASSWORD);

        // Act & Assert
        assertThatThrownBy(() -> authenticator.validate(null, credentials))
                .isInstanceOf(EmailNotConfirmedException.class);
    }

    @Test
    @DisplayName("should throw AccountNotApprovedException when user status is PENDING_APPROVAL")
    void validate_pendingApproval_throwsAccountNotApprovedException() {
        // Arrange
        User actualUser = buildUser(
                UUID.randomUUID(),
                NAME,
                PasswordEncoder.encode(PASSWORD),
                EMAIL,
                Role.FLIGHT_MANAGER,
                UserStatus.PENDING_APPROVAL,
                false,
                false
        );
        given(userDAO.findByEmail(EMAIL)).willReturn(Optional.of(actualUser));
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(EMAIL, PASSWORD);

        // Act & Assert
        assertThatThrownBy(() -> authenticator.validate(null, credentials))
                .isInstanceOf(AccountNotApprovedException.class);
    }

    @Test
    @DisplayName("should throw AccountBlockedException when user status is BLOCKED")
    void validate_blocked_throwsAccountBlockedException() {
        // Arrange
        User actualUser = buildUser(
                UUID.randomUUID(),
                NAME,
                PasswordEncoder.encode(PASSWORD),
                EMAIL,
                Role.FLIGHT_MANAGER,
                UserStatus.BLOCKED,
                false,
                false
        );
        given(userDAO.findByEmail(EMAIL)).willReturn(Optional.of(actualUser));
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(EMAIL, PASSWORD);

        // Act & Assert
        assertThatThrownBy(() -> authenticator.validate(null, credentials))
                .isInstanceOf(AccountBlockedException.class);
    }
}
