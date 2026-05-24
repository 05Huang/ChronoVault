-- Agent info for registered servers
CREATE TABLE agent_info (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE UNIQUE,
    agent_version VARCHAR(50),
    capabilities JSONB,
    last_heartbeat_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'OFFLINE',
    created_at TIMESTAMP DEFAULT NOW()
);

-- Extend events with task linkage and metadata
ALTER TABLE events ADD COLUMN task_id BIGINT REFERENCES async_tasks(id) ON DELETE SET NULL;
ALTER TABLE events ADD COLUMN metadata JSONB;

CREATE INDEX idx_events_task_id ON events(task_id);
