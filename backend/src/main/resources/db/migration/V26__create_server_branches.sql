-- Create server_branches table
CREATE TABLE server_branches (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_from_snapshot_id BIGINT REFERENCES snapshots(id) ON DELETE SET NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(server_id, name)
);

-- Add branch_id FK to snapshots table
ALTER TABLE snapshots ADD COLUMN branch_id BIGINT REFERENCES server_branches(id) ON DELETE SET NULL;

-- Create indexes
CREATE INDEX idx_server_branches_server_id ON server_branches(server_id);
CREATE INDEX idx_snapshots_branch_id ON snapshots(branch_id);

-- Insert default "main" branch for all existing servers
INSERT INTO server_branches (server_id, name, description, is_default)
SELECT id, 'main', '默认分支', TRUE
FROM servers
WHERE id NOT IN (SELECT server_id FROM server_branches WHERE is_default = TRUE);
