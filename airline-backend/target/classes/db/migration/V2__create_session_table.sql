CREATE TABLE sessions
(
    id               VARCHAR(64) PRIMARY KEY,
    user_id          UUID REFERENCES users (id) ON DELETE CASCADE,
    session_data     JSONB                    NOT NULL DEFAULT '{}',
    ip_address       VARCHAR(45),
    user_agent       VARCHAR(500),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
);

    CREATE INDEX idx_sessions_user_id ON session(user_id);
    CREATE INDEX idx_sessions_expires_at ON session(expires_at);
    CREATE INDEX idx_sessions_last_accessed_at ON session(last_accessed_at);

-- Comments
COMMENT ON TABLE sessions IS 'Active user sessions with sliding expiration';
COMMENT ON COLUMN sessions.session_data IS 'JSON blob containing session attributes';
