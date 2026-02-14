-- Create simple_users table if not exists
CREATE TABLE IF NOT EXISTS simple_users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    is_admin BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

-- Create app_user_access table if not exists  
CREATE TABLE IF NOT EXISTS app_user_access (
    id SERIAL PRIMARY KEY,
    app_id VARCHAR(255) NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(app_id, user_email)
);

-- Create chat_history table for persisting conversations
CREATE TABLE IF NOT EXISTS chat_history (
    id SERIAL PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    app_id VARCHAR(255) NOT NULL,
    conversation_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create uploaded_files table for per-conversation file uploads
CREATE TABLE IF NOT EXISTS uploaded_files (
    id SERIAL PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    conversation_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT DEFAULT 0,
    content_type VARCHAR(255),
    extracted_text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create audit_log table for tracking user activity
CREATE TABLE IF NOT EXISTS audit_log (
    id SERIAL PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for chat_history lookups
CREATE INDEX IF NOT EXISTS idx_chat_history_user_app ON chat_history (user_email, app_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_conversation ON chat_history (conversation_id);
CREATE INDEX IF NOT EXISTS idx_uploaded_files_conversation ON uploaded_files (conversation_id);
CREATE INDEX IF NOT EXISTS idx_uploaded_files_user ON uploaded_files (user_email);
CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log (user_email);
CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log (created_at);

-- Seed a default admin user (auto-provisioning handles all other users)
-- Password: "password123" — existing users' passwords are NOT overwritten
INSERT INTO simple_users (email, password_hash, full_name, is_admin) VALUES
('admin@example.com', '$2a$10$nLr86a0.LSvh8PKrlWTJd.L/hJNgY1qzM0EXQSwWip9BZ5sAOR4qK', 'Admin User', true)
ON CONFLICT (email) DO NOTHING;
