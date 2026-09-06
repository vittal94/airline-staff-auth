package com.airline.airlinebackend.util;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.dto.response.ErrorResponse;
import com.airline.airlinebackend.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ValidationUtil {
    private final static Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    private final static Pattern UPPER_CASE_PATTERN = Pattern.compile("[A-Z]");
    private final static Pattern LOWER_CASE_PATTERN = Pattern.compile("[a-z]");
    private final static Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private final static Pattern SPECIAL_PATTERN = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");

    //Common passwords to reject (top 100 most common)
    private final static List<String> COMMON_PASSWORDS = List.of(
            "password", "123456", "12345678", "qwerty", "abc123", "monkey", "1234567",
            "letmein", "trustno1", "dragon", "baseball", "iloveyou", "master", "sunshine",
            "ashley", "bailey", "passw0rd", "shadow", "123123", "654321", "superman",
            "qazwsx", "michael", "football", "password1", "password123", "welcome"
    );

    private ValidationUtil() {}

    public static void validateEmail(final String email) {
        if ( email == null || email.isEmpty() ) {
            throw new ValidationException("Email is required");
        }

        if ( !EMAIL_PATTERN.matcher(email).matches() ) {
            throw new ValidationException("Email is not valid");
        }

        if ( email.length() > 255) {
            throw new ValidationException("Email is too long (max 255 characters)");
        }
    }

    public static void validatePassword(final String password) {
        AppConfig config = AppConfig.getInstance();
        List<ErrorResponse.FieldError> errors = new ArrayList<>();

        if ( password == null || password.isEmpty() ) {
            throw new ValidationException("Password is required");
        }
        if ( password.length() < config.getPasswordMinLength() ) {
            errors.add(new ErrorResponse.FieldError(
                    "password", "Password must be at least "
                    + config.getPasswordMinLength() + " characters"));
        }
        if ( config.isPasswordRequireUppercase() && !UPPER_CASE_PATTERN.matcher(password).find() ) {
            errors.add(new ErrorResponse.FieldError(
                    "password", "Password must contain uppercase characters"
            ));
        }
        if ( config.isPasswordRequireLowercase() && !LOWER_CASE_PATTERN.matcher(password).find() ) {
            errors.add(new ErrorResponse.FieldError(
                    "password", "Password must contain lower case characters"
            ));
        }
        if ( config.isPasswordRequireDigit() && !DIGIT_PATTERN.matcher(password).find() ) {
            errors.add(new ErrorResponse.FieldError(
                    "password", "Password must contain digit characters"
            ));
        }
        if ( config.isPasswordRequireSpecial() && !SPECIAL_PATTERN.matcher(password).find() ) {
            errors.add(new ErrorResponse.FieldError(
                    "password", "Password must contain special characters"
            ));
        }
        if ( COMMON_PASSWORDS.contains(password.toLowerCase()) ) {
            errors.add(new ErrorResponse.FieldError(
                    "password", "Password is too common, please choose a stronger password"
            ));
        }
        if ( !errors.isEmpty() ) {
            throw new ValidationException("Password does not meet requirements", errors);
        }
    }

    public static void validatePasswordMatch(String password, String confirmPassword) {
        if (password != null && confirmPassword != null ) {
            if (!password.equals(confirmPassword)) {
                throw new ValidationException("Passwords do not match");
            }
        } else throw new NullPointerException("Password or confirmPassword is null");
    }

    public static void validateName(String name) {
        if (name == null || name.isBlank()) {
            return; // Name is optional
        }

        if (name.length() > 255) {
            throw new ValidationException("Name too long (max 255 characters)");
        }

        // Allow letters, spaces, hyphens, apostrophes
        if (!name.matches("^[\\p{L}\\s'-]+$")) {
            throw new ValidationException("Name contains invalid characters");
        }
    }

    public static void validateRole(String role) {
        if (role == null || role.isBlank()) {
            throw new ValidationException("Role is required");
        }

        try {
            com.airline.airlinebackend.model.enums.Role.fromValue(role);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid role: " + role);
        }
    }

    public static void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required");
        }
    }


}
