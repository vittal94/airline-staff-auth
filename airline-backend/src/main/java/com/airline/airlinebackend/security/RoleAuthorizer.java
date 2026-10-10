package com.airline.airlinebackend.security;

import com.airline.airlinebackend.model.enums.Role;
import org.pac4j.core.authorization.authorizer.Authorizer;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PAC4J Authorizer for role-based access control
 */
public class RoleAuthorizer implements Authorizer {
    private static final Logger logger = LoggerFactory.getLogger(RoleAuthorizer.class);

    private final Set<String> allowedRoles;

    public RoleAuthorizer(Role... roles) {
        this.allowedRoles = Set.of(roles).stream()
                .map(Role::getValue)
                .collect(Collectors.toSet());
    }

    public RoleAuthorizer(Set<Role> roles) {
        this.allowedRoles = roles.stream()
                .map(Role::getValue)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isAuthorized(WebContext webContext, SessionStore sessionStore, List<UserProfile> profiles) {
        if(profiles == null || profiles.isEmpty()) {
            logger.debug("Authorization is failed: no profiles.");
            return false;
        }

        for(UserProfile profile : profiles) {
            Set<String> roles = profile.getRoles();

            for(String userRole : roles) {
                if(allowedRoles.contains(userRole)) {
                    logger.debug("Authorization is successful for role: {} ", userRole);
                    return true;
                }
            }
        }
        logger.debug("Authorization is failed: user roles {} not in allowed roles {}.",
                profiles.getFirst().getRoles(), allowedRoles);
        return false;
    }
}
