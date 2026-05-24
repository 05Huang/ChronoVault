-- Extend storage_targets with credentials and config
ALTER TABLE storage_targets ADD COLUMN credentials_encrypted TEXT;
ALTER TABLE storage_targets ADD COLUMN config JSONB;

-- Widen type column to support new storage types: LOCAL, S3, OSS, WEBDAV, BLOCK, ARCHIVE
ALTER TABLE storage_targets ALTER COLUMN type TYPE VARCHAR(20);
