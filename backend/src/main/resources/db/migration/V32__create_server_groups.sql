-- Create server_groups table
CREATE TABLE server_groups (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    environment_type VARCHAR(30) NOT NULL DEFAULT 'DEVELOPMENT',
    color VARCHAR(20) NOT NULL DEFAULT '#0058BE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add group_id FK to servers table
ALTER TABLE servers ADD COLUMN group_id BIGINT REFERENCES server_groups(id) ON DELETE SET NULL;

CREATE INDEX idx_server_groups_user_id ON server_groups(user_id);
CREATE INDEX idx_servers_group_id ON servers(group_id);