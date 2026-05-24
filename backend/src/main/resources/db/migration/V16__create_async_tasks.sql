-- Async task execution tracking
CREATE TABLE async_tasks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    server_id BIGINT REFERENCES servers(id) ON DELETE SET NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress INTEGER DEFAULT 0,
    message TEXT,
    result JSONB,
    error TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_task_user ON async_tasks(user_id);
CREATE INDEX idx_task_status ON async_tasks(status);
CREATE INDEX idx_task_server ON async_tasks(server_id);
CREATE INDEX idx_task_created ON async_tasks(created_at DESC);
