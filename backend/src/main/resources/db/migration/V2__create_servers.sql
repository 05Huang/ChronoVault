CREATE TABLE servers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    os VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    uptime_seconds BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_servers_user_id ON servers(user_id);

CREATE TABLE containers (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    cpu_percent DOUBLE PRECISION,
    memory_percent DOUBLE PRECISION,
    memory_mb BIGINT,
    disk_io VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_containers_server_id ON containers(server_id);

CREATE TABLE volumes (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    container_path VARCHAR(500),
    host_path VARCHAR(500),
    size_bytes BIGINT,
    status VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_volumes_server_id ON volumes(server_id);
