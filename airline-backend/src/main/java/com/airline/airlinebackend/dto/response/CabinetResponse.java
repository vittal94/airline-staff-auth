package com.airline.airlinebackend.dto.response;

import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.emums.Role;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Cabinet (user profile) response DTO
public class CabinetResponse {
    private UUID id;
    private String name;
    private String email;
    private String role;
    private Set<String> permissions;
    private boolean passwordChangeRequired;
    private boolean firstLoginComplete;

    public static CabinetResponse fromUser(User user) {
        CabinetResponse cabinetResponse = new CabinetResponse();
        cabinetResponse.id = user.getId();
        cabinetResponse.name = user.getName();
        cabinetResponse.email = user.getEmail();
        cabinetResponse.role = user.getRole().toString();
        cabinetResponse.passwordChangeRequired = user.isPasswordChangeRequired();
        cabinetResponse.firstLoginComplete = user.isFirstLoginCompleted();
        cabinetResponse.permissions = user.getRole().getPermissions()
                .stream()
                .map(Role.Permission::toString)
                .collect(Collectors.toSet());

        return cabinetResponse;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public boolean isFirstLoginComplete() {
        return firstLoginComplete;
    }
}
