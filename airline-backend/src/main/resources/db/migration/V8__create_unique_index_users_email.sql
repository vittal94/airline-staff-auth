ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_user_email;
CREATE UNIQUE INDEX IF NOT EXISTS uq_idx_users_lower_emil ON users (LOWER(email));