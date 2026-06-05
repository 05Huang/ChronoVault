-- Add user_agent column to audit_logs table
ALTER TABLE audit_logs ADD COLUMN user_agent VARCHAR(500);
