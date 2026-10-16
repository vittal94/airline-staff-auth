package com.airline.airlinebackend.service;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.dao.EmailConfirmationTokenDAO;
import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.exception.ResourceNotFoundException;
import com.airline.airlinebackend.exception.ValidationException;
import com.airline.airlinebackend.model.EmailConfirmationToken;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.security.PasswordEncoder;
import com.airline.airlinebackend.util.SecureTokenGenerator;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Properties;

/**
 * Service for sending emails
 */
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final EmailConfirmationTokenDAO tokenDAO;
    private final UserDAO userDAO;
    private final AppConfig config;
    private final Session mailSession;

    public EmailService() {
        this(new EmailConfirmationTokenDAO(), new UserDAO(), AppConfig.getInstance());
    }

    public EmailService(
            EmailConfirmationTokenDAO tokenDAO,
            UserDAO userDAO,
            AppConfig config
    ) {
        this.tokenDAO = tokenDAO;
        this.userDAO = userDAO;
        this.config = config;
        this.mailSession = createMailSession();
    }

    private Session createMailSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(config.getSmtpPort()));
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.starttls.enable", "false");

        return Session.getInstance(props);
    }

    /**
     * Send email confirmation link to user
     */
    public void sendConfirmationEmail(User user) {
        //Delete any existing tokens for this user
        tokenDAO.deleteByUserId(user.getId());

        //Create new token
        String token = SecureTokenGenerator.generateEmailToken();
        String tokenHash = PasswordEncoder.hashToken(token);

        Instant expiresAt = Instant.now()
                .plus(config.getEmailConfirmationExpiryHours(), ChronoUnit.HOURS);
        EmailConfirmationToken confirmationToken = new EmailConfirmationToken(user.getId(), tokenHash, expiresAt);
        tokenDAO.save(confirmationToken);

        //Build confirmation URL
        String confirmationURL = config.getAppBaseUrl() + "api/auth/confirm-email?token=" + token;

        //Send email
        String subject = "Confirm your email - Airline Staff System";
        String body = buildConfirmationEmailBody(user, confirmationURL);

        sendEmail(user.getEmail(), subject, body);

        logger.info("Confirmation email sent to: {} ", user.getEmail());
    }

    /**
     * Validates email confirmation token.
     */
    public User validateConfirmationToken(String token) {
        String tokenHash = PasswordEncoder.hashToken(token);

        EmailConfirmationToken confirmationToken = tokenDAO.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException("Invalid or expired confirmation token"));

        if (confirmationToken.isExpired()) {
            throw new ValidationException("Confirmation token has expired. Please request a new one.");
        }

        if (confirmationToken.isUsed()) {
            throw new ValidationException("Confirmation token has already been used.");
        }

        // Mark token as used
        tokenDAO.markUsed(confirmationToken.getId());

        // Get and return user
        return userDAO.findById(confirmationToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User",
                        confirmationToken.getUserId().toString()));
    }

    private String buildConfirmationEmailBody(User user, String confirmationUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .button { 
                        display: inline-block; 
                        padding: 12px 24px; 
                        background-color: #007bff; 
                        color: white; 
                        text-decoration: none; 
                        border-radius: 4px; 
                        margin: 20px 0;
                    }
                    .footer { margin-top: 30px; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>Welcome to Airline Staff System</h2>
                    <p>Hello%s,</p>
                    <p>Thank you for registering. Please confirm your email address by clicking the button below:</p>
                    <a href="%s" class="button">Confirm Email</a>
                    <p>Or copy and paste this link into your browser:</p>
                    <p><a href="%s">%s</a></p>
                    <p>This link will expire in %d hours.</p>
                    <p>After confirming your email, your account will need to be approved by an administrator before you can log in.</p>
                    <div class="footer">
                        <p>If you didn't create this account, please ignore this email.</p>
                        <p>Airline Staff System</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                user.getName() != null ? " " + user.getName() : "",
                confirmationUrl,
                confirmationUrl,
                confirmationUrl,
                config.getEmailConfirmationExpiryHours()
        );
    }

    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            Message message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(config.getSmtpFrom(),config.getSmtpFromName()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=utf-8");

            Transport.send(message);

            logger.debug("Email sent to: {} ", to);
        } catch (Exception e) {
            logger.error("Failed to send email to: {} ", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
