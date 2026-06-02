-- Add auto_snapshot_enabled field to servers table
ALTER TABLE servers ADD COLUMN auto_snapshot_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servers ADD COLUMN last_auto_snapshot_at TIMESTAMP;