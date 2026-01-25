package com.airline.airlinebackend.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    private static final Properties properties = new Properties();
    private static volatile AppConfig instance;

    // Database
    private final String dbUrl;
    private final String dbUser;
    private final String dbPass;
    private final int dbPoolSize;
    private final int dbPoolMinIdle;

    // Session
    private final int sessionTimeoutMinutes;
    private final String sessionCookieName;
    private final boolean sessionCookieSecure;
    private final String sessionCookieSameSite;

    // Remember me
    private final String rememberMeCookieName;
    private final int rememberMeDurationHours;

    // CSRF
    private final String csrfCookieName;
    private final String csrfHeaderName;

    // SMTP mailpit
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpFrom;
    private final String smtpFromName;

    // Application
    private final String appBaseUrl;
    private final int emailConfirmationExpiryHours;

    // Initial admin
    private final String initialAdminEmail;
    private final String initialAdminPassword;

    // Rate limit
    private final int rateLimitLoginMaxAttempts;
    private final int rateLimitLoginWindowMinutes;
    private final int rateLimitIpMaxAttempts;
    private final int rateLimitIpWindowMinutes;

    // Password policy
    private final int passwordMinLength;
    private final boolean passwordRequireUppercase;
    private final boolean passwordRequireLowercase;
    private final boolean passwordRequireDigit;
    private final boolean passwordRequireSpecial;

    private AppConfig() {
        loadProperties();

        // Database
        this.dbUrl = getProperty("db.url", "jdbc:postgresql://localhost:5432/airline_db");
        this.dbUser = getProperty("db.user", "airline_user");
        this.dbPass = getProperty("db.pass", "");
        this.dbPoolSize = getIntProperty("db.pool.size", 10);
        this.dbPoolMinIdle = getIntProperty("db.pool.min.idle", 2);

        // Session
        this.sessionTimeoutMinutes = getIntProperty("session.timeout.minutes", 30);
        this.sessionCookieName = getProperty("session.cookie.name", "AIRLINE_SESSION");
        this.sessionCookieSecure = getBooleanProperty("session.cookie.secure", true);
        this.sessionCookieSameSite = getProperty("session.cookie.sameSite", "Strict");

        // Remember-Me
        this.rememberMeCookieName = getProperty("remember.me.cookie.name", "AIRLINE_REMEMBER_ME");
        this.rememberMeDurationHours = getIntProperty("remember.me.duration.hours", 24);

        // CSRF
        this.csrfCookieName = getProperty("csrf.cookie.name", "XSRF-TOKEN");
        this.csrfHeaderName = getProperty("csrf.header.name", "X-XSRF-TOKEN");

        // SMTP
        this.smtpHost = getProperty("smtp.host", "localhost");
        this.smtpPort = getIntProperty("smtp.port", 1025);
        this.smtpFrom = getProperty("smtp.from", "noreply@airline.com");
        this.smtpFromName = getProperty("smtp.from.name", "Airline Staff System");

        // Application
        this.appBaseUrl = getProperty("app.base.url", "http://localhost:8080");
        this.emailConfirmationExpiryHours = getIntProperty("email.confirmation.expiry.hours", 24);

        // Initial Admin
        this.initialAdminEmail = getProperty("initial.admin.email", "admin@airline.com");
        this.initialAdminPassword = getProperty("initial.admin.password", "Admin@Initial123!");

        // Rate Limiting
        this.rateLimitLoginMaxAttempts = getIntProperty("rate.limit.login.max.attempts", 5);
        this.rateLimitLoginWindowMinutes = getIntProperty("rate.limit.login.window.minutes", 15);
        this.rateLimitIpMaxAttempts = getIntProperty("rate.limit.ip.max.attempts", 20);
        this.rateLimitIpWindowMinutes = getIntProperty("rate.limit.ip.window.minutes", 30);

        // Password Policy
        this.passwordMinLength = getIntProperty("password.min.length", 12);
        this.passwordRequireUppercase = getBooleanProperty("password.require.uppercase", true);
        this.passwordRequireLowercase = getBooleanProperty("password.require.lowercase", true);
        this.passwordRequireDigit = getBooleanProperty("password.require.digit", true);
        this.passwordRequireSpecial = getBooleanProperty("password.require.special", true);
    }

    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = new AppConfig();
                }
            }
        }
        return instance;
    }

    private void loadProperties() {
        try(InputStream input = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties",e);
        }
    }

    private String getProperty(String key, String defaultValue) {
        String value = properties.getProperty(key, defaultValue);
        if ( value != null ) {

            // If in application.properties contain placeholder from .env file like
            // db.url=${DB_UR}, properties.getProperty() will return the row text
            // In this case need to manually load it from environment variables
            // For the following code to work its required to use EnvFile plugin
            if (value.startsWith("$") && value.endsWith("}")) {
                value = value.substring(2, value.length()-2);
                var envValue = System.getenv(value);
                if ( envValue != null && !envValue.isEmpty() ) return envValue;
            }

            String sysValue = System.getProperty(key);
            if ( sysValue != null && !sysValue.isEmpty() ) return sysValue;
        }
        return value;
    }

    private int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    public String getDbUrl() { return dbUrl; }
    public String getDbUsername() { return dbUser; }
    public String getDbPassword() { return dbPass; }
    public int getDbPoolSize() { return dbPoolSize; }
    public int getDbPoolMinIdle() { return dbPoolMinIdle; }

    public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
    public String getSessionCookieName() { return sessionCookieName; }
    public boolean isSessionCookieSecure() { return sessionCookieSecure; }
    public String getSessionCookieSameSite() { return sessionCookieSameSite; }

    public String getRememberMeCookieName() { return rememberMeCookieName; }
    public int getRememberMeDurationHours() { return rememberMeDurationHours; }

    public String getCsrfCookieName() { return csrfCookieName; }
    public String getCsrfHeaderName() { return csrfHeaderName; }

    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getSmtpFrom() { return smtpFrom; }
    public String getSmtpFromName() { return smtpFromName; }

    public String getAppBaseUrl() { return appBaseUrl; }
    public int getEmailConfirmationExpiryHours() { return emailConfirmationExpiryHours; }

    public String getInitialAdminEmail() { return initialAdminEmail; }
    public String getInitialAdminPassword() { return initialAdminPassword; }

    public int getRateLimitLoginMaxAttempts() { return rateLimitLoginMaxAttempts; }
    public int getRateLimitLoginWindowMinutes() { return rateLimitLoginWindowMinutes; }
    public int getRateLimitIpMaxAttempts() { return rateLimitIpMaxAttempts; }
    public int getRateLimitIpWindowMinutes() { return rateLimitIpWindowMinutes; }

    public int getPasswordMinLength() { return passwordMinLength; }
    public boolean isPasswordRequireUppercase() { return passwordRequireUppercase; }
    public boolean isPasswordRequireLowercase() { return passwordRequireLowercase; }
    public boolean isPasswordRequireDigit() { return passwordRequireDigit; }
    public boolean isPasswordRequireSpecial() { return passwordRequireSpecial; }


}
