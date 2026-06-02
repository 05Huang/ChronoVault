-- Add verified column to snapshots table
ALTER TABLE snapshots ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE snapshots ADD COLUMN verified_at TIMESTAMP;