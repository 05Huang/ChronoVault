-- Fix state_json: convert TEXT to JSONB and add missing columns for state-aware snapshots
-- V37 created state_json as TEXT which doesn't support JSONB queries efficiently.

-- Drop the old GIN index on tsvector (incompatible with JSONB)
DROP INDEX IF EXISTS idx_snapshots_state_json;

-- Convert state_json from TEXT to JSONB
ALTER TABLE snapshots ALTER COLUMN state_json TYPE jsonb USING state_json::jsonb;

-- Add state_collected_at timestamp for when the agent collected the state
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS state_collected_at timestamp;

-- Add change_summary_json for pre-computed diff summary (avoids N+1 queries in list view)
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS change_summary_json jsonb;

-- Add previous_snapshot_id for linked-list navigation (timeline view)
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS previous_snapshot_id bigint REFERENCES snapshots(id);

-- Create proper GIN index for JSONB queries
CREATE INDEX IF NOT EXISTS idx_snapshots_state_jsonb ON snapshots USING gin(state_json);

-- Index for change summary queries
CREATE INDEX IF NOT EXISTS idx_snapshots_change_summary ON snapshots USING gin(change_summary_json);

-- Index for timeline navigation
CREATE INDEX IF NOT EXISTS idx_snapshots_previous ON snapshots(previous_snapshot_id);

-- Index for snapshot list performance (server + created_at)
CREATE INDEX IF NOT EXISTS idx_snapshots_server_created ON snapshots(server_id, created_at DESC);
