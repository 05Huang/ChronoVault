CREATE TABLE system_settings (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT,
    updated_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO system_settings (key, value) VALUES
    ('ai.enabled', 'true'),
    ('ai.base-url', 'https://api.xiaomimimo.com/v1'),
    ('ai.api-key', ''),
    ('ai.model', 'mimo-v2.5-pro'),
    ('ai.max-tokens', '4096'),
    ('ai.temperature', '0.7');
