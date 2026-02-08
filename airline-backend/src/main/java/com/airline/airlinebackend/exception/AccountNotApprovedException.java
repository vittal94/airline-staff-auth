package com.airline.airlinebackend.exception;

import jakarta.servlet.http.HttpServletResponse;

public class AccountNotApprovedException extends BaseException{
    public AccountNotApprovedException() {
        super("Account pending approval. Please wait for administrator approval.",
                "ACCOUNT_NOT_APPROVED", HttpServletResponse.SC_FORBIDDEN);
    }
}
