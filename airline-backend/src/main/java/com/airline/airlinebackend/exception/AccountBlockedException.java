package com.airline.airlinebackend.exception;

import jakarta.servlet.http.HttpServletResponse;

public class AccountBlockedException extends BaseException{
    public AccountBlockedException() {
        super("Account is blocked. Please contact administrator.",
                "ACCOUNT_BLOCKED", HttpServletResponse.SC_FORBIDDEN);
    }
}
