CREATE TABLE snapshot_retention_policies (
    id              BIGSERIAL PRIMARY KEY,
    server_id       BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    max_count       INT,
    max_age_days    INT,
    min_keep_days   INT NOT NULL DEFAULT 7,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMP,
    deleted_count   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_retention_policies_server ON snapshot_retention_policies(server_id);
CREATE INDEX idx_retention_policies_enabled ON snapshot_retention_policies(enabled) WHERE enabled = TRUE;
