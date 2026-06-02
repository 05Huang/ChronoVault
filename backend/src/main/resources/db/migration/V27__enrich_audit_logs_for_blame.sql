-- Add snapshot_id, server_id, change_type columns to audit_logs
ALTER TABLE audit_logs ADD COLUMN snapshot_id BIGINT REFERENCES snapshots(id) ON DELETE SET NULL;
ALTER TABLE audit_logs ADD COLUMN server_id BIGINT REFERENCES servers(id) ON DELETE SET NULL;
ALTER TABLE audit_logs ADD COLUMN change_type VARCHAR(50);
ALTER TABLE audit_logs ADD COLUMN resource_id BIGINT;
ALTER TABLE audit_logs ADD COLUMN details TEXT;

CREATE INDEX idx_audit_logs_snapshot_id ON audit_logs(snapshot_id);
CREATE INDEX idx_audit_logs_server_id ON audit_logs(server_id);
CREATE INDEX idx_audit_logs_change_type ON audit_logs(change_type);