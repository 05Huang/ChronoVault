-- Create webhook_endpoints table
CREATE TABLE webhook_endpoints (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(500) NOT NULL,
    secret VARCHAR(200),
    events TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create webhook_delivery_logs table
CREATE TABLE webhook_delivery_logs (
    id BIGSERIAL PRIMARY KEY,
    webhook_id BIGINT NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    response_code INTEGER,
    error TEXT,
    attempt INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_delivery_logs_webhook_id ON webhook_delivery_logs(webhook_id);
CREATE INDEX idx_webhook_delivery_logs_created_at ON webhook_delivery_logs(created_at DESC);