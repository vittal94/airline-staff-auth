CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100),
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT ('PENDING_EMAIL_CONFIRMATION'),
    password_change_required BOOLEAN NOT NULL DEFAULT false,
    first_login_completed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT chk_user_role CHECK ( role IN ('ADMIN', 'FLIGHT_MANAGER', 'CUSTOMER_MANAGER')),
    CONSTRAINT chk_user_status CHECK ( status IN ('PENDING_EMAIL_CONFIRMATION',
                                                 'PENDING_APPROVAL','ACTIVE','BLOCKED'))
);

--INDEXES
CREATE INDEX idx_users_email ON user(email);
CREATE INDEX idx_users_status ON user(status);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_created_at ON users(created_at);

-- COMMENTS
COMMENT ON TABLE user IS 'Airline staff user accounts';
COMMENT ON COLUMN users.status IS 'Account status is: PENDING_EMAIL_CONFIRMATION,
                                                 PENDING_APPROVAL,ACTIVE,BLOCKED';

