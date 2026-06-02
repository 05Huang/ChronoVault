-- Create container_states table for Docker state capture
CREATE TABLE container_states (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES snapshots(id) ON DELETE CASCADE,
    container_name VARCHAR(200) NOT NULL,
    image VARCHAR(200),
    status VARCHAR(50),
    ports TEXT,
    volumes TEXT,
    networks TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_container_states_snapshot_id ON container_states(snapshot_id);