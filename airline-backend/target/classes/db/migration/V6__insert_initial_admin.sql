-- Insert initial admin user
-- Password will be read from environment variable during flyway migration
-- The password hash is for initialPass! (Argon2id)
-- In production, use environment variable substitution

INSERT INTO users(
                  id,
                  email,
                  password_hash,
                  name,
                  role,
                  status,
                  password_change_required,
                  first_login_complete,
                  created_at
) VALUES (
          gen_random_uued(),
          COALESCE('${INITIAL_ADMIN_EMAIL}','admin@airline.com'),
          '$argon2id$v=19$m=65536,t=3,p=4$7+6AJoMQYaWeBndc6YTcEw$ssZ3oYTNtPr4qspKA7TpRXmZ3uQjqygGFHL9QJwxpU4',
          'System administrator',
          'ADMIN',
          'ACTIVE',
          TRUE, -- Require password change on first login
          FALSE, -- First login not completed
          CURRENT_TIMESTAMP
         );

-- Note: The actual password hash needs to be generated at deployment time
-- Use the AppContextListener to update this hash on first startup
