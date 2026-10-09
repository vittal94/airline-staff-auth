package com.airline.airlinebackend.security;

import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.exception.AccountBlockedException;
import com.airline.airlinebackend.exception.AccountNotApprovedException;
import com.airline.airlinebackend.exception.AuthenticationException;
import com.airline.airlinebackend.exception.EmailNotConfirmedException;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.enums.Role;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.profile.CommonProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PAC4J Authenticator implementation that validates credentials against the database
 */
public class DatabaseAuthenticator implements Authenticator {
    private static Logger logger = LoggerFactory.getLogger(DatabaseAuthenticator.class);

    private final UserDAO userDAO;

    public DatabaseAuthenticator() {
        this(new UserDAO());
    }

    public DatabaseAuthenticator(final UserDAO userDAO) {
        this.userDAO = userDAO;
    }
    @Override
    public Optional<Credentials> validate(CallContext callContext, Credentials credentials) {
        if(!(credentials instanceof UsernamePasswordCredentials creds)) {
            throw new CredentialsException("Invalid credentials type");
        }

        String email = creds.getUsername();
        String password = creds.getPassword();

        logger.debug("Authenticating user {}", email);

        Optional<User> userOpt = userDAO.findByEmail(email);

        if(userOpt.isEmpty()) {
            logger.debug("User {} not found", email);
            throw new AuthenticationException("Invalid email or password");
        }

        User user = userOpt.get();

        //Verify password
        if(!PasswordEncoder.verify(password, user.getPasswordHash())) {
            logger.debug("Invalid password of user {}", email);
            throw new AuthenticationException("Invalid password");
        }

        //Check account status
        switch (user.getStatus()) {
            case PENDING_EMAIL_CONFIRMATION -> throw new EmailNotConfirmedException();
            case PENDING_APPROVAL -> throw new AccountNotApprovedException();
            case BLOCKED -> throw new AccountBlockedException();
            case ACTIVE -> {}
        }

        //Create PAC4J profile
        CommonProfile profile = new CommonProfile();
        profile.setId(user.getId().toString());
        profile.addAttribute("email", user.getEmail());
        profile.addAttribute("name", user.getName());
        profile.addAttribute("role", user.getRole().getValue());
        profile.addAttribute("status", user.getStatus().getValue());
        profile.addAttribute("passwordChangeRequired", user.isPasswordChangeRequired());
        profile.addAttribute("firstLoginCompleted", user.isFirstLoginCompleted());
        profile.addRole(user.getRole().getValue());

        //Add permissions to profile
        Set<String> permissions = user.getRole().getPermissions().stream()
                        .map(Role.Permission::name)
                                .collect(Collectors.toSet());

        profile.addAttribute("permissions", permissions);

        creds.setUserProfile(profile);
        logger.debug("User {} authenticated successfully", email);
        return Optional.of(credentials);
    }
}
