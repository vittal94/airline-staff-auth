package unittests.security;

import com.airline.airlinebackend.model.enums.Role;
import com.airline.airlinebackend.security.RoleAuthorizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.UserProfile;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RoleAuthorizer}.
 * <p>
 * Verifies role-based authorization decisions for single and multiple profiles,
 * empty or missing profiles, and both varargs and {@link Set} constructors.
 */
@DisplayName("RoleAuthorizer")
public class RoleAuthorizerTest {

    @Test
    @DisplayName("should authorize when user has one of the allowed roles")
    void isAuthorized_matchingRole_returnsTrue() {
        // Arrange
        RoleAuthorizer authorizer = new RoleAuthorizer(Role.ADMIN, Role.FLIGHT_MANAGER);
        UserProfile profile = createProfileWithRoles(Role.CUSTOMER_MANAGER, Role.FLIGHT_MANAGER);

        // Act
        boolean result = authorizer.isAuthorized(null, null, List.of(profile));

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should authorize when user has the only allowed role")
    void isAuthorized_singleAllowedRoleAndMatch_returnsTrue() {
        // Arrange
        RoleAuthorizer authorizer = new RoleAuthorizer(Role.ADMIN);
        UserProfile profile = createProfileWithRoles(Role.ADMIN);

        // Act
        boolean result = authorizer.isAuthorized(null, null, List.of(profile));

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should deny authorization when user role is not in allowed roles")
    void isAuthorized_noMatchingRole_returnsFalse() {
        // Arrange
        RoleAuthorizer authorizer = new RoleAuthorizer(Role.ADMIN, Role.FLIGHT_MANAGER);
        UserProfile profile = createProfileWithRoles(Role.CUSTOMER_MANAGER);

        // Act
        boolean result = authorizer.isAuthorized(null, null, List.of(profile));

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should deny authorization when profiles list is empty")
    void isAuthorized_emptyProfiles_returnsFalse() {
        // Arrange
        RoleAuthorizer authorizer = new RoleAuthorizer(Role.ADMIN);

        // Act
        boolean result = authorizer.isAuthorized(null, null, Collections.emptyList());

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should deny authorization when profiles list is null")
    void isAuthorized_nullProfiles_returnsFalse() {
        // Arrange
        RoleAuthorizer authorizer = new RoleAuthorizer(Role.ADMIN);

        // Act
        boolean result = authorizer.isAuthorized(null, null, null);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should authorize when second profile has an allowed role")
    void isAuthorized_secondProfileHasAllowedRole_returnsTrue() {
        // Arrange
        RoleAuthorizer authorizer = new RoleAuthorizer(Role.FLIGHT_MANAGER);
        UserProfile firstProfile = createProfileWithRoles(Role.CUSTOMER_MANAGER);
        UserProfile secondProfile = createProfileWithRoles(Role.FLIGHT_MANAGER);

        // Act
        boolean result = authorizer.isAuthorized(null, null, List.of(firstProfile, secondProfile));

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should deny authorization when all profiles lack allowed roles")
    void isAuthorized_multipleProfilesNoMatch_returnsFalse() {
        // Arrange
        RoleAuthorizer authorizer = new RoleAuthorizer(Role.ADMIN);
        UserProfile firstProfile = createProfileWithRoles(Role.CUSTOMER_MANAGER);
        UserProfile secondProfile = createProfileWithRoles(Role.FLIGHT_MANAGER);

        // Act
        boolean result = authorizer.isAuthorized(null, null, List.of(firstProfile, secondProfile));

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should work with Set constructor and authorize matching role")
    void isAuthorized_setConstructorMatchingRole_returnsTrue() {
        // Arrange
        RoleAuthorizer authorizer = new RoleAuthorizer(Set.of(Role.ADMIN, Role.CUSTOMER_MANAGER));
        UserProfile profile = createProfileWithRoles(Role.CUSTOMER_MANAGER);

        // Act
        boolean result = authorizer.isAuthorized(null, null, List.of(profile));

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should be case-sensitive when comparing role values")
    void isAuthorized_caseSensitiveRoleComparison_returnsFalse() {
        // Arrange
        RoleAuthorizer authorizer = new RoleAuthorizer(Role.ADMIN);
        CommonProfile profile = new CommonProfile();
        profile.addRole("admin");

        // Act
        boolean result = authorizer.isAuthorized(null, null, List.of(profile));

        // Assert
        assertThat(result).isFalse();
    }

    private UserProfile createProfileWithRoles(Role... roles) {
        CommonProfile profile = new CommonProfile();
        for (Role role : roles) {
            profile.addRole(role.getValue());
        }
        return profile;
    }
}
