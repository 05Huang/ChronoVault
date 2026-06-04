-- P1-2: Add composite index for container_states query optimization
-- Covers findBySnapshotIdOrderByContainerNameAsc with filter + sort in a single index
CREATE INDEX IF NOT EXISTS idx_container_states_snapshot_container
    ON container_states(snapshot_id, container_name);
