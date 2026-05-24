CREATE TABLE snapshot_tags (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES snapshots(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(20) DEFAULT '#0058be',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    CONSTRAINT uq_snapshot_tag_name UNIQUE (snapshot_id, name)
);

CREATE INDEX idx_snapshot_tags_snapshot_id ON snapshot_tags(snapshot_id);
CREATE INDEX idx_snapshot_tags_name ON snapshot_tags(name);
