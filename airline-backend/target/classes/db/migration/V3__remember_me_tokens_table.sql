CREATE TABLE remember_me_tokens (
    series VARCHAR(64) PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_remember_me_token_user_id ON remember_me_tokens(user_id);
CREATE INDEX idx_remember_me_token_expires_at ON remember_me_tokens(expires_at);

COMMENT ON TABLE remember_me_tokens IS 'Persistent login tokens with series-based theft detection';
COMMENT ON COLUMN remember_me_tokens.series IS 'Stable identifier for the token series (device)';
COMMENT ON COLUMN remember_me_tokens.token_hash IS 'Hashed rotating token value';