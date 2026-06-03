-- P3-2: Performance optimization indexes
-- Snapshot list query: server_id + created_at for fast listing
CREATE INDEX IF NOT EXISTS idx_snapshots_server_created ON snapshots(server_id, created_at DESC);

-- Event query: created_at for activity trend
CREATE INDEX IF NOT EXISTS idx_events_created_at ON events(created_at DESC);

-- Alert query: status + severity for dashboard
CREATE INDEX IF NOT EXISTS idx_alerts_status_severity ON alerts(status, severity);

-- Alert query: server_id for server-specific alerts
CREATE INDEX IF NOT EXISTS idx_alerts_server_created ON alerts(server_id, created_at DESC);

-- Snapshot state_json GIN index for JSONB queries
CREATE INDEX IF NOT EXISTS idx_snapshots_state_gin ON snapshots USING gin(state_json);

-- Storage target query: type for storage summary
CREATE INDEX IF NOT EXISTS idx_storage_targets_type ON storage_targets(type);

-- Server query: status for active server count
CREATE INDEX IF NOT EXISTS idx_servers_status ON servers(status);
