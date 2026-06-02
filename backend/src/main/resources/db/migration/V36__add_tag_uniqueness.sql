-- Add unique constraint on snapshot_tags.name (global uniqueness for tag names)
-- First, ensure no duplicate names exist across snapshots
DELETE FROM snapshot_tags WHERE id NOT IN (
    SELECT MIN(id) FROM snapshot_tags GROUP BY name
);

-- Now add the unique constraint
ALTER TABLE snapshot_tags ADD CONSTRAINT uk_snapshot_tags_name UNIQUE (name);