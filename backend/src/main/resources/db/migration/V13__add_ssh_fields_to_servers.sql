-- SSH connection fields for remote server management
ALTER TABLE servers ADD COLUMN ssh_port INTEGER DEFAULT 22;
ALTER TABLE servers ADD COLUMN ssh_username VARCHAR(100) DEFAULT 'root';
ALTER TABLE servers ADD COLUMN ssh_key_encrypted TEXT;
ALTER TABLE servers ADD COLUMN ssh_auth_method VARCHAR(20) DEFAULT 'KEY';
ALTER TABLE servers ADD COLUMN agent_id VARCHAR(100);

-- Index for agent lookups
CREATE INDEX idx_servers_agent_id ON servers(agent_id);
