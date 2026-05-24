CREATE TABLE risks (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT REFERENCES servers(id) ON DELETE SET NULL,
    level VARCHAR(20) NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    ai_suggestion TEXT,
    action_text VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    discovered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risks_status ON risks(status);
