CREATE TABLE snapshots (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'STABLE',
    hash VARCHAR(64),
    size_bytes BIGINT,
    type VARCHAR(20) DEFAULT 'FULL',
    note VARCHAR(500),
    microservice_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_snapshots_server_id ON snapshots(server_id);
CREATE INDEX idx_snapshots_created_at ON snapshots(created_at DESC);

CREATE TABLE snapshot_diffs (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES snapshots(id) ON DELETE CASCADE,
    file_path VARCHAR(500) NOT NULL,
    prev_value VARCHAR(500),
    next_value VARCHAR(500)
);

CREATE INDEX idx_snapshot_diffs_snapshot_id ON snapshot_diffs(snapshot_id);
