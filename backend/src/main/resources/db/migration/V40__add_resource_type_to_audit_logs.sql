-- Add resource_type column to audit_logs for resource categorization
ALTER TABLE audit_logs ADD COLUMN resource_type VARCHAR(50);
CREATE INDEX idx_audit_logs_resource_type ON audit_logs(resource_type);
CREATE INDEX idx_audit_logs_resource_type_id ON audit_logs(resource_type, resource_id);