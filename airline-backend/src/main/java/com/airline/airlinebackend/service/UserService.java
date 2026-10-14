package com.airline.airlinebackend.service;

import com.airline.airlinebackend.dao.UserDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for user management operations
 */
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserDAO userDAO;
    private final EmailService emailService;

    public UserService() {
        this.userDAO = new UserDAO();
        this.emailService = new EmailService();
    }


}
