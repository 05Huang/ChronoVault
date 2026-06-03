-- Add state_json column to snapshots for storing system state snapshot data
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS state_json TEXT;

-- Create index for state_json queries
CREATE INDEX IF NOT EXISTS idx_snapshots_state_json ON snapshots USING gin(to_tsvector('english', COALESCE(state_json, '')));
