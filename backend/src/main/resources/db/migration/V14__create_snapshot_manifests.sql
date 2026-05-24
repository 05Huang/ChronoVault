-- Snapshot file manifests for diff tracking
CREATE TABLE snapshot_manifests (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES snapshots(id) ON DELETE CASCADE,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT,
    file_hash VARCHAR(64),
    file_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_manifest_snapshot ON snapshot_manifests(snapshot_id);
CREATE INDEX idx_manifest_path ON snapshot_manifests(file_path);
