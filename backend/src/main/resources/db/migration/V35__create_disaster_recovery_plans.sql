-- Create disaster_recovery_plans table
CREATE TABLE disaster_recovery_plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    steps TEXT,
    estimated_rto INTEGER,
    estimated_rpo INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    last_executed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);