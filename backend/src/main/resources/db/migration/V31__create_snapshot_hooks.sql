-- Create snapshot_hooks table
CREATE TABLE snapshot_hooks (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    hook_type VARCHAR(30) NOT NULL,
    command TEXT NOT NULL,
    timeout_seconds INTEGER NOT NULL DEFAULT 60,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshot_hooks_server_id ON snapshot_hooks(server_id);
CREATE INDEX idx_snapshot_hooks_hook_type ON snapshot_hooks(hook_type);