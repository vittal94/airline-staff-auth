CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS INTEGER AS $$
DECLARE
       deleted_count INTEGER;
BEGIN
       -- Delete expired session
       WITH deleted AS (
           DELETE FROM sessions
           WHERE expires_at < CURRENT_TIMESTAMP
           RETURNING id
       )
       SELECT count(*) INTO deleted_count FROM deleted;

       -- Delete expired remember me tokens
       DELETE FROM remember_me_tokens
       WHERE expires_at < CURRENT_TIMESTAMP;

       -- Delete expired email confirmation tokens
       DELETE FROM email_confirmation_tokens
       WHERE expires_at < CURRENT_TIMESTAMP;

       -- Delete old login attempts
       DELETE FROM login_attempts
       WHERE attempted_at < CURRENT_TIMESTAMP - INTERVAL '24 hours';

       RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

   -- Create a scheduled job using pg_cron (if available)
-- Note: pg_cron extension must be installed and configured in PostgreSQL
-- This is typically done outside of Flyway in production

-- Alternatively, call this function from an external cron job:
-- SELECT cleanup_expired_sessions();

COMMENT ON FUNCTION cleanup_expired_sessions IS
'Cleans up expired sessions, remember-me tokens, email tokens, and old login attempts.
Call every 5-15 minutes via pg_cron or external scheduler.';