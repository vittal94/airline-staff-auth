package com.airline.airlinebackend.service;

import com.airline.airlinebackend.dao.SessionDAO;
import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.dto.request.LoginRequest;
import com.airline.airlinebackend.exception.AccountBlockedException;
import com.airline.airlinebackend.exception.AccountNotApprovedException;
import com.airline.airlinebackend.exception.AuthenticationException;
import com.airline.airlinebackend.exception.BaseException;
import com.airline.airlinebackend.exception.EmailNotConfirmedException;
import com.airline.airlinebackend.model.LoginAttempt;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.enums.UserStatus;
import com.airline.airlinebackend.security.PasswordEncoder;
import com.airline.airlinebackend.security.PostgresSessionStore;
import com.airline.airlinebackend.security.RememberMeService;
import com.airline.airlinebackend.util.ServletUtil;
import com.airline.airlinebackend.util.ValidationUtil;
import com.airline.airlinebackend.util.XssSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.pac4j.core.context.WebContext;
import org.pac4j.jee.context.JEEContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Authentication service handling login, logout, and session management
 */
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserDAO userDao;
    private final SessionDAO sessionDao;
    private final RateLimitService rateLimitService;
    private final RememberMeService rememberMeService;
    private final UserService userService;

    public AuthService() {
        this(
                new UserDAO(),
                new SessionDAO(),
                new RateLimitService(),
                new RememberMeService(),
                new UserService()
        );
    }

    public AuthService(
            UserDAO userDAO,
            SessionDAO sessionDAO,
            RateLimitService rateLimitService,
            RememberMeService rememberMeService,
            UserService userService
    ) {
        this.userDao = userDAO;
        this.sessionDao = sessionDAO;
        this.rateLimitService = rateLimitService;
        this.rememberMeService = rememberMeService;
        this.userService = userService;
    }

    /**
     * Authenticates user and creates a session
     */
    public User login(
            LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            PostgresSessionStore sessionStore
    ) {
       String email = XssSanitizer.sanitizeEmail(request.getEmail());
       String ipAddress = ServletUtil.getClientIP(httpRequest);

        ValidationUtil.validateEmail(email);
        ValidationUtil.validateNotBlank(request.getPassword(), "Password");

        //Check rate limit
        rateLimitService.checkRateLimit(email, ipAddress);

        try{
            //Find user
            Optional<User> userOpt = userDao.findByEmail(email);

            if(userOpt.isEmpty()) {
                rateLimitService.recordFailedAttempt(email, ipAddress);
                throw new AuthenticationException("Invalid email or password");
            }
            User user = userOpt.get();

            //Verify password
            if(!PasswordEncoder.verify(request.getPassword(),user.getPasswordHash())) {
                rateLimitService.recordFailedAttempt(email, ipAddress);
                throw new AuthenticationException("Invalid email or password");
            }

            //Validate account status
            validateAccountStatus(user);

            //Record successful login
            rateLimitService.recordSuccessfulLogin(email, ipAddress);

            //Create PAC4J context
            WebContext context = new JEEContext(httpRequest, httpResponse);

            // Rotate session ID and bind the user in one step (prevents session fixation)
            sessionStore.renewSessionForUser(context, user.getId());

            //Create remember-me token
            rememberMeService.createRememberMeToken(httpRequest,httpResponse,user.getId());

            //Update last login
            userService.updateLastLogin(user.getId());

            logger.info("User logged in: {}", email);
            return user;
        } catch (AuthenticationException e) {
            throw e;
        } catch (BaseException e) {
            // Domain exceptions (e.g. AccountBlockedException, EmailNotConfirmedException)
            // carry their own HTTP status — do not wrap them into a generic login failure
            throw e;
        } catch (Exception e) {
            logger.error("Login error for email: {}", email, e);
            throw new AuthenticationException("Login failed",e);
        }
    }

    /**
     * Attempts to auto login using remember-me token
     */
    public Optional<User> tryToRememberMeLogin(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            PostgresSessionStore sessionStore
    ) {
        Optional<User> optUser = rememberMeService.validateAndRotate(httpRequest, httpResponse);

        if(optUser.isPresent()) {
            User user = optUser.get();

            //Verify account is still active
            if(user.getStatus() != UserStatus.ACTIVE) {
                logger.debug("Remember-me login rejected: account not active");
                rememberMeService.clearRememberMe(httpRequest, httpResponse);
                return Optional.empty();
            }

            //Create session
            WebContext context = new JEEContext(httpRequest, httpResponse);
            sessionStore.renewSessionForUser(context, user.getId());

            userService.updateLastLogin(user.getId());

            logger.info("User logged in via remember-me: {}", user.getEmail());
        }
        return optUser;
    }

    /**
     *Logout user and invalidate session
     */
    public void logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            PostgresSessionStore sessionStore
    ) {
        WebContext context = new JEEContext(httpRequest, httpResponse);

        //Get userId before destroying session
        Optional<UUID> useId = sessionStore.getUserId(context);

        //Clear remember-me token
        rememberMeService.clearRememberMe(httpRequest, httpResponse);

        //Destroy session
        sessionStore.destroySession(context);

        useId.ifPresent(id -> logger.info("User logged out: {}", id));
    }

    /**
     * Change password and invalidate all sessions/tokens
     */
    public void changePassword(
            UUID userId,
            String currentPassword,
            String newPassword,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            PostgresSessionStore sessionStore
            ) {
        //Change password through userService
        userService.changePassword(userId, currentPassword, newPassword);

        //Invalidate all user's remember-me tokens
        rememberMeService.clearAllUserTokens(userId);

        //Invalidate all session except current
        WebContext context = new JEEContext(httpRequest, httpResponse);
        Optional<String> currentSessionId = sessionStore.getSessionId(context,false);

        sessionDao.deleteByUserId(userId);

        //Rotate session id
        sessionStore.renewSessionForUser(context, userId);

        //Create new remember-me token
        rememberMeService.createRememberMeToken(httpRequest,httpResponse,userId);

        logger.info("Password changed and sessions invalidates for the user: {}", userId);
    }

    /**
     * Get current authenticated user from session
     */
    public Optional<User> getCurrentUser(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            PostgresSessionStore sessionStore
    ) {
        WebContext context = new JEEContext(httpRequest, httpResponse);
        Optional<UUID> useId = sessionStore.getUserId(context);

        if(useId.isEmpty()) {
            return Optional.empty();
        }
        return userDao.findById(useId.get());
    }

    private void validateAccountStatus(User user) {
        switch(user.getStatus()) {
            case PENDING_EMAIL_CONFIRMATION -> throw new EmailNotConfirmedException();
            case PENDING_APPROVAL -> throw new AccountNotApprovedException();
            case BLOCKED -> throw new AccountBlockedException();
            case ACTIVE -> {}
        }
    }
}
