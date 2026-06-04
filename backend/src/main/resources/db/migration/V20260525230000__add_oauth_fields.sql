-- ===================================================================
-- Flyway Database Migration
-- Version: V20260525230000__add_oauth_fields.sql
-- Description: Alter users table to support OAuth2 social login
-- ===================================================================

-- 1. Alter password column to be nullable
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- 2. Add oauth_provider and oauth_id columns
ALTER TABLE users ADD COLUMN oauth_provider VARCHAR(20) DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN oauth_id VARCHAR(100);

-- 3. Create a unique index for OAuth2 logins (provider + oauth_id)
CREATE UNIQUE INDEX idx_users_oauth ON users(oauth_provider, oauth_id);
