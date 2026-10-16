package unittests.service;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.dao.EmailConfirmationTokenDAO;
import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.exception.ResourceNotFoundException;
import com.airline.airlinebackend.exception.ValidationException;
import com.airline.airlinebackend.model.EmailConfirmationToken;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.enums.Role;
import com.airline.airlinebackend.security.PasswordEncoder;
import com.airline.airlinebackend.service.EmailService;
import com.airline.airlinebackend.util.SecureTokenGenerator;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Unit tests for {@link EmailService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailService")
class EmailServiceTest {

    private static final String SMTP_HOST = "localhost";
    private static final int SMTP_PORT = 1025;
    private static final String SMTP_FROM = "noreply@airline.com";
    private static final String SMTP_FROM_NAME = "Airline Staff System";
    private static final String APP_BASE_URL = "http://localhost:8080/";
    private static final int EMAIL_EXPIRY_HOURS = 24;
    private static final String RAW_TOKEN = "raw-email-token";
    private static final String TOKEN_HASH = "hashed-email-token";

    @Mock
    private EmailConfirmationTokenDAO tokenDAO;

    @Mock
    private UserDAO userDAO;

    @Mock
    private AppConfig config;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        given(config.getSmtpHost()).willReturn(SMTP_HOST);
        given(config.getSmtpPort()).willReturn(SMTP_PORT);
        given(config.getSmtpFrom()).willReturn(SMTP_FROM);
        given(config.getSmtpFromName()).willReturn(SMTP_FROM_NAME);
        given(config.getAppBaseUrl()).willReturn(APP_BASE_URL);
        given(config.getEmailConfirmationExpiryHours()).willReturn(EMAIL_EXPIRY_HOURS);

        emailService = new EmailService(tokenDAO, userDAO, config);
    }

    private User createUser(String name) {
        return User.builder()
                .id(UUID.randomUUID())
                .name(name)
                .email("john.doe@airline.com")
                .passwordHash("passwordHash")
                .role(Role.FLIGHT_MANAGER)
                .build();
    }

    private EmailConfirmationToken createToken(UUID userId, Instant expiresAt, Instant lastUsedAt) {
        EmailConfirmationToken token = new EmailConfirmationToken(userId, TOKEN_HASH, expiresAt);
        if (lastUsedAt != null) {
            token.setLastUsedAt(lastUsedAt);
        }
        return token;
    }

    @Nested
    @DisplayName("sendConfirmationEmail()")
    class SendConfirmationEmailTests {

        @Test
        @DisplayName("should delete old token, save new token and send HTML confirmation email")
        void sendConfirmationEmail_validUser_deletesOldTokenSavesNewTokenAndSendsEmail() throws Exception {
            // Arrange
            User user = createUser("John");
            Instant expectedExpiry = Instant.now().plus(EMAIL_EXPIRY_HOURS, ChronoUnit.HOURS);

            ArgumentCaptor<EmailConfirmationToken> tokenCaptor = ArgumentCaptor.forClass(EmailConfirmationToken.class);
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

            try (MockedStatic<SecureTokenGenerator> tokenGenerator = mockStatic(SecureTokenGenerator.class);
                 MockedStatic<PasswordEncoder> passwordEncoder = mockStatic(PasswordEncoder.class);
                 MockedStatic<Transport> transport = mockStatic(Transport.class)) {

                tokenGenerator.when(SecureTokenGenerator::generateEmailToken).thenReturn(RAW_TOKEN);
                passwordEncoder.when(() -> PasswordEncoder.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);

                // Act
                emailService.sendConfirmationEmail(user);

                // Assert
                then(tokenDAO).should().deleteByUserId(user.getId());
                then(tokenDAO).should().save(tokenCaptor.capture());

                EmailConfirmationToken savedToken = tokenCaptor.getValue();
                assertThat(savedToken.getUserId()).isEqualTo(user.getId());
                assertThat(savedToken.getTokenHash()).isEqualTo(TOKEN_HASH);
                assertThat(savedToken.getExpiresAt()).isCloseTo(expectedExpiry, within(1, ChronoUnit.SECONDS));

                transport.verify(() -> Transport.send(messageCaptor.capture()), times(1));
                Message sentMessage = messageCaptor.getValue();
                assertThat(sentMessage.getSubject()).isEqualTo("Confirm your email - Airline Staff System");

                Address[] fromAddresses = sentMessage.getFrom();
                assertThat(fromAddresses).hasSize(1);
                assertThat(((InternetAddress) fromAddresses[0]).getAddress()).isEqualTo(SMTP_FROM);
                assertThat(((InternetAddress) fromAddresses[0]).getPersonal()).isEqualTo(SMTP_FROM_NAME);

                Address[] recipients = sentMessage.getRecipients(Message.RecipientType.TO);
                assertThat(recipients).hasSize(1);
                assertThat(((InternetAddress) recipients[0]).getAddress()).isEqualTo(user.getEmail());

                String body = (String) sentMessage.getContent();
                String expectedUrl = APP_BASE_URL + "api/auth/confirm-email?token=" + RAW_TOKEN;
                assertThat(body).contains(expectedUrl);
                assertThat(body).contains("Hello John,");
                assertThat(body).contains("This link will expire in " + EMAIL_EXPIRY_HOURS + " hours.");
            }
        }

        @Test
        @DisplayName("should render greeting without name when user name is null")
        void sendConfirmationEmail_nullUserName_rendersGreetingWithoutName() throws Exception {
            // Arrange
            User user = createUser(null);

            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

            try (MockedStatic<SecureTokenGenerator> tokenGenerator = mockStatic(SecureTokenGenerator.class);
                 MockedStatic<PasswordEncoder> passwordEncoder = mockStatic(PasswordEncoder.class);
                 MockedStatic<Transport> transport = mockStatic(Transport.class)) {

                tokenGenerator.when(SecureTokenGenerator::generateEmailToken).thenReturn(RAW_TOKEN);
                passwordEncoder.when(() -> PasswordEncoder.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);

                // Act
                emailService.sendConfirmationEmail(user);

                // Assert
                transport.verify(() -> Transport.send(messageCaptor.capture()), times(1));
                String body = (String) messageCaptor.getValue().getContent();
                assertThat(body).contains("Hello,");
                assertThat(body).doesNotContain("Hello null");
            }
        }
    }

    @Nested
    @DisplayName("validateConfirmationToken()")
    class ValidateConfirmationTokenTests {

        @Test
        @DisplayName("should mark token used and return associated user when token is valid")
        void validateConfirmationToken_validToken_marksUsedAndReturnsUser() {
            // Arrange
            User user = createUser("John");
            EmailConfirmationToken token = createToken(user.getId(), Instant.now().plus(1, ChronoUnit.HOURS), null);

            try (MockedStatic<PasswordEncoder> passwordEncoder = mockStatic(PasswordEncoder.class)) {
                passwordEncoder.when(() -> PasswordEncoder.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
                given(tokenDAO.findByTokenHash(TOKEN_HASH)).willReturn(Optional.of(token));
                given(userDAO.findById(user.getId())).willReturn(Optional.of(user));

                // Act
                User result = emailService.validateConfirmationToken(RAW_TOKEN);

                // Assert
                assertThat(result).isEqualTo(user);
                then(tokenDAO).should().markUsed(token.getId());
            }
        }

        @Test
        @DisplayName("should throw ValidationException when token is not found")
        void validateConfirmationToken_tokenNotFound_throwsValidationException() {
            // Arrange
            try (MockedStatic<PasswordEncoder> passwordEncoder = mockStatic(PasswordEncoder.class)) {
                passwordEncoder.when(() -> PasswordEncoder.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
                given(tokenDAO.findByTokenHash(TOKEN_HASH)).willReturn(Optional.empty());

                // Act / Assert
                assertThatThrownBy(() -> emailService.validateConfirmationToken(RAW_TOKEN))
                        .isInstanceOf(ValidationException.class)
                        .hasMessageContaining("Invalid or expired confirmation token");

                then(tokenDAO).should(never()).markUsed(any(UUID.class));
                then(userDAO).should(never()).findById(any(UUID.class));
            }
        }

        @Test
        @DisplayName("should throw ValidationException when token has expired")
        void validateConfirmationToken_expiredToken_throwsValidationException() {
            // Arrange
            EmailConfirmationToken token = createToken(UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.MINUTES), null);

            try (MockedStatic<PasswordEncoder> passwordEncoder = mockStatic(PasswordEncoder.class)) {
                passwordEncoder.when(() -> PasswordEncoder.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
                given(tokenDAO.findByTokenHash(TOKEN_HASH)).willReturn(Optional.of(token));

                // Act / Assert
                assertThatThrownBy(() -> emailService.validateConfirmationToken(RAW_TOKEN))
                        .isInstanceOf(ValidationException.class)
                        .hasMessageContaining("Confirmation token has expired");

                then(tokenDAO).should(never()).markUsed(any(UUID.class));
                then(userDAO).should(never()).findById(any(UUID.class));
            }
        }

        @Test
        @DisplayName("should throw ValidationException when token has already been used")
        void validateConfirmationToken_alreadyUsedToken_throwsValidationException() {
            // Arrange
            EmailConfirmationToken token = createToken(UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.HOURS), Instant.now());

            try (MockedStatic<PasswordEncoder> passwordEncoder = mockStatic(PasswordEncoder.class)) {
                passwordEncoder.when(() -> PasswordEncoder.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
                given(tokenDAO.findByTokenHash(TOKEN_HASH)).willReturn(Optional.of(token));

                // Act / Assert
                assertThatThrownBy(() -> emailService.validateConfirmationToken(RAW_TOKEN))
                        .isInstanceOf(ValidationException.class)
                        .hasMessageContaining("Confirmation token has already been used");

                then(tokenDAO).should(never()).markUsed(any(UUID.class));
                then(userDAO).should(never()).findById(any(UUID.class));
            }
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user is missing for a valid token")
        void validateConfirmationToken_userNotFound_throwsResourceNotFoundException() {
            // Arrange
            UUID userId = UUID.randomUUID();
            EmailConfirmationToken token = createToken(userId, Instant.now().plus(1, ChronoUnit.HOURS), null);

            try (MockedStatic<PasswordEncoder> passwordEncoder = mockStatic(PasswordEncoder.class)) {
                passwordEncoder.when(() -> PasswordEncoder.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
                given(tokenDAO.findByTokenHash(TOKEN_HASH)).willReturn(Optional.of(token));
                given(userDAO.findById(userId)).willReturn(Optional.empty());

                // Act / Assert
                assertThatThrownBy(() -> emailService.validateConfirmationToken(RAW_TOKEN))
                        .isInstanceOf(ResourceNotFoundException.class)
                        .hasMessageContaining("User")
                        .hasMessageContaining(userId.toString());

                then(tokenDAO).should().markUsed(token.getId());
            }
        }
    }
}
