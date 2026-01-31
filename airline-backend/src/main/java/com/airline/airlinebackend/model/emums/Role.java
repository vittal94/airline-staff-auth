package com.airline.airlinebackend.model.emums;

import java.util.Arrays;
import java.util.Set;

// User roles with hierarchical permissions
public enum Role {

    ADMIN("ADMIN", Set.of(
            Permission.ADMIN_DASHBOARD,
            Permission.FLIGHT_MANAGER_DASHBOARD,
            Permission.CUSTOMER_MANAGER_DASHBOARD,
            Permission.MANAGE_USERS,
            Permission.VIEW_ALL_USERS
    )),
    FLIGHT_MANAGER("FLIGHT_MANAGER", Set.of(
            Permission.FLIGHT_MANAGER_DASHBOARD,
            Permission.CUSTOMER_MANAGER_DASHBOARD
    )),
    CUSTOMER_MANAGER("CUSTOMER_MANAGER", Set.of(Permission.CUSTOMER_MANAGER_DASHBOARD));


    private final String value;
    private final Set<Permission> permissions;

    Role(String value, Set<Permission> permissions) {
        this.value = value;
        this.permissions = permissions;
    }

    public String getValue() {return value;}

    public Set<Permission> getPermissions() {return permissions;}

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public static Role fromValue(String value) {
        return Arrays.stream(values())
                .filter(role -> role.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + value));
    }

    public enum Permission {
        ADMIN_DASHBOARD,
        FLIGHT_MANAGER_DASHBOARD,
        CUSTOMER_MANAGER_DASHBOARD,
        MANAGE_USERS,
        VIEW_ALL_USERS,
    }
}
