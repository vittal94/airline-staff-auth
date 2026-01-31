-- Login attempts for rate limiting
CREATE TABLE login_attempts (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    success BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX edx_login_attempts_email ON login_attempts(email);
CREATE INDEX edx_login_attempts_ip ON login_attempts(ip_address);
CREATE INDEX edx_login_attempts_time ON login_attempts(attempted_at);

-- Indexes for rate limit queries
CREATE INDEX edx_login_attempts_email_time ON login_attempts(email,attempted_at);
CREATE INDEX edx_login_attempts_ip_time ON login_attempts(ip_address,attempted_at);

COMMENT ON TABLE login_attempts IS 'Tracks login attempts for rate limiting and security monitoring';