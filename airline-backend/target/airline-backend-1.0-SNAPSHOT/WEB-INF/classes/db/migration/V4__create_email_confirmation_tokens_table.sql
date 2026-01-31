CREATE TABLE email_confirmation_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uued(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL

CONSTRAINT uk_email_token_hash UNIQUE(token_hash)
);

CREATE INDEX idx_email_confirm_user_id ON email_confirmation_tokens(user_id);
CREATE INDEX idx_email_confirm_expires_at ON email_confirmation_tokens(expires_at);

COMMENT ON TABLE email_confirmation_tokens IN 'Tokens for email address verification'
