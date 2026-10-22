package unittests.service;

import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.dto.request.RegisterRequest;
import com.airline.airlinebackend.dto.request.UpdateCabinetRequest;
import com.airline.airlinebackend.exception.AuthenticationException;
import com.airline.airlinebackend.exception.AuthorizationException;
import com.airline.airlinebackend.exception.DuplicateResourceException;
import com.airline.airlinebackend.exception.ResourceNotFoundException;
import com.airline.airlinebackend.exception.ValidationException;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.enums.Role;
import com.airline.airlinebackend.model.enums.UserStatus;
import com.airline.airlinebackend.security.PasswordEncoder;
import com.airline.airlinebackend.service.EmailService;
import com.airline.airlinebackend.service.UserService;
import com.airline.airlinebackend.util.ValidationUtil;
import com.airline.airlinebackend.util.XssSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link UserService}.
 */
@DisplayName("UserService")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String EMAIL = "test@test.com";
    private static final String PASSWORD = "Password123.";
    private static final String ROLE = "FLIGHT_MANAGER";

    @Mock
    private UserDAO userDAO;

    @Mock
    private EmailService emailService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userDAO, emailService);
    }

    private RegisterRequest createRegisterRequest(
            String email, String password, String confirmPassword, String role
    ) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        request.setRole(role);
        return request;
    }

    private RegisterRequest createRegisterRequest() {
        return createRegisterRequest(EMAIL, PASSWORD, PASSWORD, ROLE);
    }

    private User createUser(
            UUID id,
            String email,
            Role role,
            UserStatus status,
            boolean firstLoginCompleted
    ) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash("passwordHash")
                .role(role)
                .status(status)
                .firstLoginCompleted(firstLoginCompleted)
                .build();
    }

    private UpdateCabinetRequest createUpdateCabinetRequest(String name, String email) {
        UpdateCabinetRequest request = new UpdateCabinetRequest();
        request.setName(name);
        request.setEmail(email);
        return request;
    }

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("should register new user, sanitize and validate inputs, save user and send confirmation email")
        void register_validRequest_savesUserAndSendsConfirmationEmail() {
            // Arrange
            String email = "test@test.com";
            String sanitizedEmail = email.toLowerCase();
            String password = "Password123.";
            RegisterRequest request = createRegisterRequest(email, password, password, "FLIGHT_MANAGER");

            given(userDAO.existsByEmail(sanitizedEmail)).willReturn(false);
            given(userDAO.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            // Act & Assert
            try (MockedStatic<XssSanitizer> sanitizer = mockStatic(XssSanitizer.class);
                 MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)
            ) {
                sanitizer.when(() -> XssSanitizer.sanitizeEmail(email)).thenReturn(sanitizedEmail);

                User result = userService.register(request);

                sanitizer.verify(() -> XssSanitizer.sanitizeEmail(email));
                validationUtil.verify(() -> ValidationUtil.validateEmail(sanitizedEmail));
                validationUtil.verify(() -> ValidationUtil.validatePassword(password));
                validationUtil.verify(() -> ValidationUtil.validatePasswordMatch(password, password));
                validationUtil.verify(() -> ValidationUtil.validateRole("FLIGHT_MANAGER"));

                ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                then(userDAO).should().save(userCaptor.capture());
                User savedUser = userCaptor.getValue();

                then(emailService).should().sendConfirmationEmail(eq(result));

                assertThat(result.getEmail()).isEqualTo(sanitizedEmail);
                assertThat(result.getRole()).isEqualTo(Role.FLIGHT_MANAGER);
                assertThat(PasswordEncoder.verify(password, result.getPasswordHash())).isTrue();
                assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_CONFIRMATION);
            }
        }

        @Test
        @DisplayName("should throw ValidationException when passwords do not match")
        void register_passwordsDoNotMatch_throwsValidationException() {
            // Arrange
            RegisterRequest request = createRegisterRequest(
                    "test@mail.com",
                    "Password123.",
                    "Password123",
                    "FLIGHT_MANAGER"
            );

            // Act & Assert
            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class);
                 MockedStatic<XssSanitizer> sanitizer = mockStatic(XssSanitizer.class)
            ) {
                sanitizer.when(() -> XssSanitizer.sanitizeEmail(request.getEmail()))
                        .thenReturn(request.getEmail());
                validationUtil.when(() -> ValidationUtil.validatePasswordMatch(any(), any()))
                        .thenThrow(ValidationException.class);

                assertThatThrownBy(() -> userService.register(request))
                        .isInstanceOf(ValidationException.class);

                then(userDAO).should(never()).existsByEmail(any());
                then(userDAO).should(never()).save(any());
                then(emailService).should(never()).sendConfirmationEmail(any());
            }
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when email already exists")
        void register_emailAlreadyExists_throwsDuplicateResourceException() {
            // Arrange
            RegisterRequest request = createRegisterRequest();
            given(userDAO.existsByEmail(request.getEmail())).willReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining(request.getEmail());

            then(userDAO).should(never()).save(any());
            then(emailService).should(never()).sendConfirmationEmail(any());
        }
    }

    @Nested
    @DisplayName("confirmEmail()")
    class ConfirmEmailTests {

        @Test
        @DisplayName("should update status to PENDING_APPROVAL when user is pending email confirmation")
        void confirmEmail_pendingConfirmation_updatesStatusToPendingApproval() {
            // Arrange
            String token = "emailConfirmationToken";
            User user = createUser(
                    UUID.randomUUID(),
                    "user@test.com",
                    Role.FLIGHT_MANAGER,
                    UserStatus.PENDING_EMAIL_CONFIRMATION,
                    false
            );

            given(emailService.validateConfirmationToken(token)).willReturn(user);

            // Act
            userService.confirmEmail(token);

            // Assert
            then(userDAO).should().update_status(user.getId(), UserStatus.PENDING_APPROVAL);
        }

        @Test
        @DisplayName("should not update status when user is not pending email confirmation")
        void confirmEmail_alreadyConfirmed_doesNotUpdateStatus() {
            // Arrange
            String token = "emailConfirmationToken";
            User user = createUser(
                    UUID.randomUUID(),
                    "user@test.com",
                    Role.FLIGHT_MANAGER,
                    UserStatus.ACTIVE,
                    true
            );

            given(emailService.validateConfirmationToken(token)).willReturn(user);

            // Act
            userService.confirmEmail(token);

            // Assert
            then(userDAO).should(never()).update_status(any(), any());
        }
    }

    @Nested
    @DisplayName("resendConfirmationEmail()")
    class ResendConfirmationEmailTests {

        @Test
        @DisplayName("should send confirmation email when user exists and is pending email confirmation")
        void resendConfirmationEmail_pendingUser_sendsEmail() {
            // Arrange
            String email = "user@test.com";
            User user = createUser(
                    UUID.randomUUID(),
                    email,
                    Role.FLIGHT_MANAGER,
                    UserStatus.PENDING_EMAIL_CONFIRMATION,
                    false
            );

            given(userDAO.findByEmail(email)).willReturn(Optional.of(user));

            // Act
            userService.resendConfirmationEmail(email);

            // Assert
            then(emailService).should().sendConfirmationEmail(user);
        }

        @Test
        @DisplayName("should not send email when user does not exist")
        void resendConfirmationEmail_unknownUser_doesNotSendEmail() {
            // Arrange
            String email = "unknown@test.com";
            given(userDAO.findByEmail(email)).willReturn(Optional.empty());

            // Act
            userService.resendConfirmationEmail(email);

            // Assert
            then(emailService).should(never()).sendConfirmationEmail(any());
        }

        @Test
        @DisplayName("should not send email when user is already confirmed or approved")
        void resendConfirmationEmail_nonPendingUser_doesNotSendEmail() {
            // Arrange
            String email = "user@test.com";
            User user = createUser(
                    UUID.randomUUID(),
                    email,
                    Role.FLIGHT_MANAGER,
                    UserStatus.ACTIVE,
                    true
            );

            given(userDAO.findByEmail(email)).willReturn(Optional.of(user));

            // Act
            userService.resendConfirmationEmail(email);

            // Assert
            then(emailService).should(never()).sendConfirmationEmail(any());
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("should return user when ID exists")
        void getById_existingId_returnsUser() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            given(userDAO.findById(userId)).willReturn(Optional.of(user));

            // Act
            User result = userService.getById(userId);

            // Assert
            assertThat(result).isEqualTo(user);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when ID does not exist")
        void getById_unknownId_throwsResourceNotFoundException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            given(userDAO.findById(userId)).willReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userService.getById(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User")
                    .hasMessageContaining(userId.toString());
        }
    }

    @Nested
    @DisplayName("getByEmail()")
    class GetByEmailTests {

        @Test
        @DisplayName("should return user when email exists")
        void getByEmail_existingEmail_returnsUser() {
            // Arrange
            String email = "user@test.com";
            User user = createUser(UUID.randomUUID(), email, Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            given(userDAO.findByEmail(email)).willReturn(Optional.of(user));

            // Act
            Optional<User> result = userService.getByEmail(email);

            // Assert
            assertThat(result).isPresent().contains(user);
        }

        @Test
        @DisplayName("should return empty Optional when email does not exist")
        void getByEmail_unknownEmail_returnsEmpty() {
            // Arrange
            String email = "unknown@test.com";
            given(userDAO.findByEmail(email)).willReturn(Optional.empty());

            // Act
            Optional<User> result = userService.getByEmail(email);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsersTests {

        @Test
        @DisplayName("should return all users from DAO")
        void getAllUsers_returnsAllUsers() {
            // Arrange
            User user1 = createUser(UUID.randomUUID(), "u1@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            User user2 = createUser(UUID.randomUUID(), "u2@test.com", Role.CUSTOMER_MANAGER, UserStatus.PENDING_APPROVAL, false);
            given(userDAO.findAll()).willReturn(List.of(user1, user2));

            // Act
            List<User> result = userService.getAllUsers();

            // Assert
            assertThat(result).containsExactly(user1, user2);
        }
    }

    @Nested
    @DisplayName("getUsersByStatus()")
    class GetUsersByStatusTests {

        @Test
        @DisplayName("should return users filtered by status")
        void getUsersByStatus_activeStatus_returnsActiveUsers() {
            // Arrange
            User user = createUser(UUID.randomUUID(), "u1@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            given(userDAO.findByStatus(UserStatus.ACTIVE)).willReturn(List.of(user));

            // Act
            List<User> result = userService.getUsersByStatus(UserStatus.ACTIVE);

            // Assert
            assertThat(result).containsExactly(user);
        }
    }

    @Nested
    @DisplayName("updateCabinet()")
    class UpdateCabinetTests {

        @Test
        @DisplayName("should update name and mark first login completed")
        void updateCabinet_validName_updatesNameAndFirstLoginFlag() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UpdateCabinetRequest request = createUpdateCabinetRequest("New Name", null);
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, false);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.update(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            // Act
            try (MockedStatic<XssSanitizer> sanitizer = mockStatic(XssSanitizer.class);
                 MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)
            ) {
                sanitizer.when(() -> XssSanitizer.sanitizeName("New Name")).thenReturn("Sanitized Name");

                User result = userService.updateCabinet(userId, request);

                // Assert
                sanitizer.verify(() -> XssSanitizer.sanitizeName("New Name"));
                validationUtil.verify(() -> ValidationUtil.validateName("Sanitized Name"));
                assertThat(result.getName()).isEqualTo("Sanitized Name");
                assertThat(result.isFirstLoginCompleted()).isTrue();
                then(userDAO).should().update(result);
            }
        }

        @Test
        @DisplayName("should update email when it differs from current email and is available")
        void updateCabinet_differentAvailableEmail_updatesEmail() {
            // Arrange
            UUID userId = UUID.randomUUID();
            String currentEmail = "old@test.com";
            String newEmail = "new@test.com";
            UpdateCabinetRequest request = createUpdateCabinetRequest(null, newEmail);
            User user = createUser(userId, currentEmail, Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.existsByEmail(newEmail)).willReturn(false);
            given(userDAO.update(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            // Act
            try (MockedStatic<XssSanitizer> sanitizer = mockStatic(XssSanitizer.class);
                 MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)
            ) {
                sanitizer.when(() -> XssSanitizer.sanitizeEmail(newEmail)).thenReturn(newEmail);

                User result = userService.updateCabinet(userId, request);

                // Assert
                validationUtil.verify(() -> ValidationUtil.validateEmail(newEmail));
                assertThat(result.getEmail()).isEqualTo(newEmail);
            }
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when new email is already in use")
        void updateCabinet_duplicateEmail_throwsDuplicateResourceException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            String currentEmail = "old@test.com";
            String newEmail = "taken@test.com";
            UpdateCabinetRequest request = createUpdateCabinetRequest(null, newEmail);
            User user = createUser(userId, currentEmail, Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.existsByEmail(newEmail)).willReturn(true);

            // Act & Assert
            try (MockedStatic<XssSanitizer> sanitizer = mockStatic(XssSanitizer.class)) {
                sanitizer.when(() -> XssSanitizer.sanitizeEmail(newEmail)).thenReturn(newEmail);

                assertThatThrownBy(() -> userService.updateCabinet(userId, request))
                        .isInstanceOf(DuplicateResourceException.class)
                        .hasMessageContaining(newEmail);

                then(userDAO).should(never()).update(any());
            }
        }
    }

    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("should update password when old password is correct")
        void changePassword_correctOldPassword_updatesPassword() {
            // Arrange
            UUID userId = UUID.randomUUID();
            String oldPassword = "OldPassword123.";
            String newPassword = "NewPassword123.";
            String newHash = "newPasswordHash";
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));

            // Act
            try (MockedStatic<PasswordEncoder> passwordEncoder = mockStatic(PasswordEncoder.class);
                 MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)
            ) {
                passwordEncoder.when(() -> PasswordEncoder.verify(oldPassword, user.getPasswordHash()))
                        .thenReturn(true);
                passwordEncoder.when(() -> PasswordEncoder.encode(newPassword)).thenReturn(newHash);

                userService.changePassword(userId, oldPassword, newPassword);

                // Assert
                validationUtil.verify(() -> ValidationUtil.validatePassword(newPassword));
                then(userDAO).should().update_password(userId, newHash, false);
            }
        }

        @Test
        @DisplayName("should throw AuthenticationException when old password is incorrect")
        void changePassword_incorrectOldPassword_throwsAuthenticationException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            String oldPassword = "WrongPassword123.";
            String newPassword = "NewPassword123.";
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));

            // Act & Assert
            try (MockedStatic<PasswordEncoder> passwordEncoder = mockStatic(PasswordEncoder.class);
                 MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)
            ) {
                passwordEncoder.when(() -> PasswordEncoder.verify(oldPassword, user.getPasswordHash()))
                        .thenReturn(false);

                assertThatThrownBy(() -> userService.changePassword(userId, oldPassword, newPassword))
                        .isInstanceOf(AuthenticationException.class)
                        .hasMessageContaining("Current password is incorrect");

                validationUtil.verify(() -> ValidationUtil.validatePassword(newPassword), never());
                then(userDAO).should(never()).update_password(any(), any(), anyBoolean());
            }
        }
    }

    @Nested
    @DisplayName("approveUser()")
    class ApproveUserTests {

        @Test
        @DisplayName("should approve pending user when requested by admin")
        void approveUser_adminApprovesPendingUser_updatesStatusToActive() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.PENDING_APPROVAL, false);
            User admin = createUser(adminId, "admin@test.com", Role.ADMIN, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(adminId)).willReturn(Optional.of(admin));

            // Act
            userService.approveUser(userId, adminId);

            // Assert
            then(userDAO).should().update_status(userId, UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw AuthorizationException when requester is not an admin")
        void approveUser_nonAdminRequester_throwsAuthorizationException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.PENDING_APPROVAL, false);
            User requester = createUser(requesterId, "requester@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(requesterId)).willReturn(Optional.of(requester));

            // Act & Assert
            assertThatThrownBy(() -> userService.approveUser(userId, requesterId))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Only admins can approve users");

            then(userDAO).should(never()).update_status(any(), eq(UserStatus.ACTIVE));
        }

        @Test
        @DisplayName("should throw ValidationException when user is not pending approval")
        void approveUser_userNotPending_throwsValidationException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            User admin = createUser(adminId, "admin@test.com", Role.ADMIN, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(adminId)).willReturn(Optional.of(admin));

            // Act & Assert
            assertThatThrownBy(() -> userService.approveUser(userId, adminId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("User is not pending approval");

            then(userDAO).should(never()).update_status(any(), eq(UserStatus.ACTIVE));
        }
    }

    @Nested
    @DisplayName("blockUser()")
    class BlockUserTests {

        @Test
        @DisplayName("should block non-admin user when requested by admin")
        void blockUser_adminBlocksNonAdmin_updatesStatusToBlocked() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            User admin = createUser(adminId, "admin@test.com", Role.ADMIN, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(adminId)).willReturn(Optional.of(admin));

            // Act
            userService.blockUser(userId, adminId);

            // Assert
            then(userDAO).should().update_status(userId, UserStatus.BLOCKED);
        }

        @Test
        @DisplayName("should throw AuthorizationException when requester is not an admin")
        void blockUser_nonAdminRequester_throwsAuthorizationException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            User requester = createUser(requesterId, "requester@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(requesterId)).willReturn(Optional.of(requester));

            // Act & Assert
            assertThatThrownBy(() -> userService.blockUser(userId, requesterId))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Only admins can block users");

            then(userDAO).should(never()).update_status(any(), eq(UserStatus.BLOCKED));
        }

        @Test
        @DisplayName("should throw AuthorizationException when attempting to block an admin")
        void blockUser_targetIsAdmin_throwsAuthorizationException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            User user = createUser(userId, "admin2@test.com", Role.ADMIN, UserStatus.ACTIVE, true);
            User admin = createUser(adminId, "admin@test.com", Role.ADMIN, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(adminId)).willReturn(Optional.of(admin));

            // Act & Assert
            assertThatThrownBy(() -> userService.blockUser(userId, adminId))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Cannot block admin users");

            then(userDAO).should(never()).update_status(any(), eq(UserStatus.BLOCKED));
        }
    }

    @Nested
    @DisplayName("unblockUser()")
    class UnblockUserTests {

        @Test
        @DisplayName("should unblock blocked user when requested by admin")
        void unblockUser_adminUnblocksBlockedUser_updatesStatusToActive() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.BLOCKED, true);
            User admin = createUser(adminId, "admin@test.com", Role.ADMIN, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(adminId)).willReturn(Optional.of(admin));

            // Act
            userService.unblockUser(userId, adminId);

            // Assert
            then(userDAO).should().update_status(userId, UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw AuthorizationException when requester is not an admin")
        void unblockUser_nonAdminRequester_throwsAuthorizationException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.BLOCKED, true);
            User requester = createUser(requesterId, "requester@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(requesterId)).willReturn(Optional.of(requester));

            // Act & Assert
            assertThatThrownBy(() -> userService.unblockUser(userId, requesterId))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Only admins can unblock users");

            then(userDAO).should(never()).update_status(any(), eq(UserStatus.ACTIVE));
        }

        @Test
        @DisplayName("should throw ValidationException when user is not blocked")
        void unblockUser_userNotBlocked_throwsValidationException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            User admin = createUser(adminId, "admin@test.com", Role.ADMIN, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(adminId)).willReturn(Optional.of(admin));

            // Act & Assert
            assertThatThrownBy(() -> userService.unblockUser(userId, adminId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("User is not blocked");

            then(userDAO).should(never()).update_status(any(), eq(UserStatus.ACTIVE));
        }
    }

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUserTests {

        @Test
        @DisplayName("should delete non-admin user when requested by admin")
        void deleteUser_adminDeletesNonAdmin_deletesUser() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            User admin = createUser(adminId, "admin@test.com", Role.ADMIN, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(adminId)).willReturn(Optional.of(admin));

            // Act
            userService.deleteUser(userId, adminId);

            // Assert
            then(userDAO).should().delete(userId);
        }

        @Test
        @DisplayName("should throw AuthorizationException when requester is not an admin")
        void deleteUser_nonAdminRequester_throwsAuthorizationException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            User user = createUser(userId, "user@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            User requester = createUser(requesterId, "requester@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(requesterId)).willReturn(Optional.of(requester));

            // Act & Assert
            assertThatThrownBy(() -> userService.deleteUser(userId, requesterId))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Only admins can delete users");

            then(userDAO).should(never()).delete(any());
        }

        @Test
        @DisplayName("should throw AuthorizationException when attempting to delete an admin")
        void deleteUser_targetIsAdmin_throwsAuthorizationException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            User user = createUser(userId, "admin2@test.com", Role.ADMIN, UserStatus.ACTIVE, true);
            User admin = createUser(adminId, "admin@test.com", Role.ADMIN, UserStatus.ACTIVE, true);

            given(userDAO.findById(userId)).willReturn(Optional.of(user));
            given(userDAO.findById(adminId)).willReturn(Optional.of(admin));

            // Act & Assert
            assertThatThrownBy(() -> userService.deleteUser(userId, adminId))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Cannot delete admin users");

            then(userDAO).should(never()).delete(any());
        }
    }

    @Nested
    @DisplayName("updateLastLogin()")
    class UpdateLastLoginTests {

        @Test
        @DisplayName("should delegate to DAO")
        void updateLastLogin_validUserId_updatesLastLogin() {
            // Arrange
            UUID userId = UUID.randomUUID();

            // Act
            userService.updateLastLogin(userId);

            // Assert
            then(userDAO).should().update_last_login(userId);
        }
    }

    @Nested
    @DisplayName("getDashboardStats()")
    class GetDashboardStatsTests {

        @Test
        @DisplayName("should aggregate stats from DAO")
        void getDashboardStats_returnsAggregatedStats() {
            // Arrange
            User user1 = createUser(UUID.randomUUID(), "u1@test.com", Role.FLIGHT_MANAGER, UserStatus.ACTIVE, true);
            User user2 = createUser(UUID.randomUUID(), "u2@test.com", Role.CUSTOMER_MANAGER, UserStatus.PENDING_APPROVAL, false);
            given(userDAO.findAll()).willReturn(List.of(user1, user2));
            given(userDAO.countByStatus(UserStatus.PENDING_APPROVAL)).willReturn(1);
            given(userDAO.countByStatus(UserStatus.ACTIVE)).willReturn(1);
            given(userDAO.countByStatus(UserStatus.BLOCKED)).willReturn(0);

            // Act
            UserService.DashboardStats stats = userService.getDashboardStats();

            // Assert
            assertThat(stats.totalUsers).isEqualTo(2);
            assertThat(stats.pendingApproval).isEqualTo(1);
            assertThat(stats.activeUsers).isEqualTo(1);
            assertThat(stats.blockedUsers).isEqualTo(0);
        }
    }
}
