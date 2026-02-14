-- Create app_user_access table for managing which users can access which apps
CREATE TABLE IF NOT EXISTS app_user_access (
    id BIGSERIAL PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64) DEFAULT 'system',
    UNIQUE(app_id, user_email)
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_app_user_access_app_id ON app_user_access(app_id);
CREATE INDEX IF NOT EXISTS idx_app_user_access_email ON app_user_access(user_email);

-- Create simple_users table for chatbot authentication
CREATE TABLE IF NOT EXISTS simple_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,  -- For demo, we'll use bcrypt
    full_name VARCHAR(255),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

-- Insert demo users (password is "12345" hashed with bcrypt)
-- bcrypt hash of "12345": $2a$10$N9qo8uLOickgx2ZMRZoMye7WJ8RUe0LhqDJCKN9OXS9lMi0D8QyiO
INSERT INTO simple_users (email, password_hash, full_name) 
VALUES 
    ('john@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye7WJ8RUe0LhqDJCKN9OXS9lMi0D8QyiO', 'John Doe'),
    ('jane@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye7WJ8RUe0LhqDJCKN9OXS9lMi0D8QyiO', 'Jane Smith'),
    ('test@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye7WJ8RUe0LhqDJCKN9OXS9lMi0D8QyiO', 'Test User')
ON CONFLICT (email) DO NOTHING;

-- Grant access to demo app for demo users
-- Replace with actual app_id from your system
INSERT INTO app_user_access (app_id, user_email, created_by)
VALUES 
    ('2021941639059140610', 'john@example.com', 'system'),
    ('2021941639059140610', 'jane@example.com', 'system'),
    ('2021941639059140610', 'test@example.com', 'system')
ON CONFLICT (app_id, user_email) DO NOTHING;

COMMENT ON TABLE app_user_access IS 'Manages which users (by email) have access to which apps';
COMMENT ON TABLE simple_users IS 'Simple user table for chatbot authentication (demo only)';
