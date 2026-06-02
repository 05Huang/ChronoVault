-- Create verification_jobs table
CREATE TABLE verification_jobs (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    storage_target_id BIGINT REFERENCES storage_targets(id) ON DELETE SET NULL,
    schedule_cron VARCHAR(50) NOT NULL DEFAULT '0 * * * *',
    last_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_run_at TIMESTAMP,
    last_error TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_verification_jobs_server_id ON verification_jobs(server_id);