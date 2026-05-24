CREATE TABLE scheduled_backups (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    storage_target_id BIGINT REFERENCES storage_targets(id) ON DELETE SET NULL,
    name VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    paths TEXT,
    excludes TEXT,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    last_status VARCHAR(20),
    last_error TEXT,
    run_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scheduled_backups_user_id ON scheduled_backups(user_id);
CREATE INDEX idx_scheduled_backups_server_id ON scheduled_backups(server_id);
CREATE INDEX idx_scheduled_backups_enabled ON scheduled_backups(enabled);
CREATE INDEX idx_scheduled_backups_next_run ON scheduled_backups(next_run_at);
