package com.airline.airlinebackend.service;

import com.airline.airlinebackend.dao.SessionDAO;
import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.dto.request.LoginRequest;
import com.airline.airlinebackend.exception.AuthenticationException;
import com.airline.airlinebackend.model.LoginAttempt;
import com.airline.airlinebackend.model.User;
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
        } catch (Exception e) {
            logger.error("Login error for email: {}", email, e);
            throw new AuthenticationException("Login failed");
        }
    }
    private void validateAccountStatus(User user) {}
}
