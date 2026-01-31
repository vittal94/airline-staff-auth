package com.airline.airlinebackend.dto.request;

// Resend email confirmation request DTO

public class ResendConfirmationRequest {
    private String email;

    public ResendConfirmationRequest() {}

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
