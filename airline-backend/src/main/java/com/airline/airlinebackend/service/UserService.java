package com.airline.airlinebackend.service;

import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.dto.request.RegisterRequest;
import com.airline.airlinebackend.dto.request.UpdateCabinetRequest;
import com.airline.airlinebackend.exception.*;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.enums.Role;
import com.airline.airlinebackend.model.enums.UserStatus;
import com.airline.airlinebackend.security.PasswordEncoder;
import com.airline.airlinebackend.util.ValidationUtil;
import com.airline.airlinebackend.util.XssSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for user management operations
 */
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserDAO userDao;
    private final EmailService emailService;

    public UserService() {
        this(new UserDAO(), new EmailService());
    }

    public UserService(UserDAO userDao, EmailService emailService) {
        this.userDao = userDao;
        this.emailService = emailService;
    }

    /**
     * Register new user account
     */
    public User register(RegisterRequest req) {
        //Sanitize inputs
        String email = XssSanitizer.sanitizeEmail(req.getEmail());

        //Validate inputs
        ValidationUtil.validateEmail(email);
        ValidationUtil.validatePassword(req.getPassword());
        ValidationUtil.validatePasswordMatch(req.getPassword(), req.getConfirmPassword());
        ValidationUtil.validateRole(req.getRole());

        //Check if email already exists
        if(userDao.existsByEmail(email)) {
            throw new DuplicateResourceException("User", "email", email);
        }

        // Create user
        User user = User.builder()
                .email(email)
                .passwordHash(PasswordEncoder.encode(req.getPassword()))
                .role(Role.fromValue(req.getRole()))
                .status(UserStatus.PENDING_EMAIL_CONFIRMATION)
                .passwordChangeRequired(false)
                .firstLoginCompleted(false)
                .build();

        user = userDao.save(user);

        //Send confirmation email
        emailService.sendConfirmationEmail(user);

        logger.info("User registered: {}, with role: {}", email, user.getRole());
        return user;
    }

    /**
     * Confirm user email address
     */
    public void confirmEmail(String token) {
        User user = emailService.validateConfirmationToken(token);

        if (user.getStatus() == UserStatus.PENDING_EMAIL_CONFIRMATION) {
            userDao.update_status(user.getId(), UserStatus.PENDING_APPROVAL);
            logger.info("Email confirmed for user: {}", user.getEmail());
        }
    }

    /**
     * Resend confirmation email
     */
    public void resendConfirmationEmail(String email) {
        Optional<User> optUser = userDao.findByEmail(email);

        if (optUser.isEmpty()) {
            //Don't reveal if email exist
            logger.debug("Resend confirmation requested for non-existent email: {}", email);
            return;
        }
        User user = optUser.get();
        if (user.getStatus() != UserStatus.PENDING_EMAIL_CONFIRMATION) {
            logger.debug("Resend confirmation requested for already confirmed email: {}", email);
            return;
        }
        emailService.sendConfirmationEmail(user);
        logger.info("Confirmation email resend to user: {}", user.getEmail());
    }

    /**
     * Gets user by ID.
     */
    public User getById(UUID userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    /**
     * Gets user by email.
     */
    public Optional<User> getByEmail(String email) {
        return userDao.findByEmail(email);
    }

    /**
     * Gets all users.
     */
    public List<User> getAllUsers() {
        return userDao.findAll();
    }

    /**
     * Gets users by status.
     */
    public List<User> getUsersByStatus(UserStatus status) {
        return userDao.findByStatus(status);
    }

    /**
     * Update user's cabinet (profile) information
     */
    public User updateCabinet(UUID userId, UpdateCabinetRequest req) {
        User user = getById(userId);

        //Validate and sanitize input
        if(req.getName() != null) {
            String name = XssSanitizer.sanitizeName(req.getName());
            ValidationUtil.validateName(name);
            user.setName(name);
        }
        if(req.getEmail() != null && !req.getEmail().equals(user.getEmail())) {
            String email = XssSanitizer.sanitizeEmail(req.getEmail());
            ValidationUtil.validateEmail(email);

            if(userDao.existsByEmail(email)) {
                throw new DuplicateResourceException("User", "email", email);
            }
            user.setEmail(email);
        }
        //Mark first login as completed if updating profile
        if(!user.isFirstLoginCompleted()) {
            user.setFirstLoginCompleted(true);
        }
        return userDao.update(user);
    }

    /**
     * Change user's password
     */
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = getById(userId);

        //Verify the current password
        if(!PasswordEncoder.verify(oldPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Current password is incorrect");
        }

        //Validate new password
        ValidationUtil.validatePassword(newPassword);

        //Update password
        String newHash = PasswordEncoder.encode(newPassword);
        userDao.update_password(userId,newHash,false);

        logger.info("Changed password for user: {}", user.getEmail());
    }

    /**
     * Approves a pending user account (admin action).
     */
    public void approveUser(UUID userId, UUID adminId) {
        User user = getById(userId);
        User admin = getById(adminId);

        if (admin.getRole() != Role.ADMIN) {
            throw new AuthorizationException("Only admins can approve users");
        }

        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new ValidationException("User is not pending approval");
        }

        userDao.update_status(userId, UserStatus.ACTIVE);
        logger.info("User {} approved by admin {}", user.getEmail(), admin.getEmail());
    }

    /**
     * Blocks a user account (admin action).
     */
    public void blockUser(UUID userId, UUID adminId) {
        User user = getById(userId);
        User admin = getById(adminId);

        if (admin.getRole() != Role.ADMIN) {
            throw new AuthorizationException("Only admins can block users");
        }

        // Admins cannot block other admins
        if (user.getRole() == Role.ADMIN) {
            throw new AuthorizationException("Cannot block admin users");
        }

        userDao.update_status(userId, UserStatus.BLOCKED);
        logger.info("User {} blocked by admin {}", user.getEmail(), admin.getEmail());
    }

    /**
     * Unblocks a user account (admin action).
     */
    public void unblockUser(UUID userId, UUID adminId) {
        User user = getById(userId);
        User admin = getById(adminId);

        if (admin.getRole() != Role.ADMIN) {
            throw new AuthorizationException("Only admins can unblock users");
        }

        if (user.getStatus() != UserStatus.BLOCKED) {
            throw new ValidationException("User is not blocked");
        }

        userDao.update_status(userId, UserStatus.ACTIVE);
        logger.info("User {} unblocked by admin {}", user.getEmail(), admin.getEmail());
    }

    /**
     * Deletes a user account (admin action).
     */
    public void deleteUser(UUID userId, UUID adminId) {
        User user = getById(userId);
        User admin = getById(adminId);

        if (admin.getRole() != Role.ADMIN) {
            throw new AuthorizationException("Only admins can delete users");
        }

        // Admins cannot delete other admins
        if (user.getRole() == Role.ADMIN) {
            throw new AuthorizationException("Cannot delete admin users");
        }

        userDao.delete(userId);
        logger.info("User {} deleted by admin {}", user.getEmail(), admin.getEmail());
    }

    /**
     * Updates last login time.
     */
    public void updateLastLogin(UUID userId) {
        userDao.update_last_login(userId);
    }

    public DashboardStats getDashboardStats() {
        DashboardStats stats = new DashboardStats();
        stats.totalUsers = userDao.findAll().size();
        stats.pendingApproval = userDao.countByStatus(UserStatus.PENDING_APPROVAL);
        stats.activeUsers = userDao.countByStatus(UserStatus.ACTIVE);
        stats.blockedUsers = userDao.countByStatus(UserStatus.BLOCKED);
        return stats;
    }

    public static class DashboardStats {
        public int totalUsers;
        public int pendingApproval;
        public int activeUsers;
        public int blockedUsers;
    }

}
