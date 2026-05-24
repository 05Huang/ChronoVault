-- V20: Clean all demo/seed data
-- Keep only the first user (owner) for login

-- Delete dependent data first (foreign key order)
DELETE FROM snapshot_diffs;
DELETE FROM snapshot_manifests;
DELETE FROM snapshots;
DELETE FROM containers;
DELETE FROM volumes;
DELETE FROM alerts;
DELETE FROM alert_rules;
DELETE FROM risks;
DELETE FROM ai_insights;
DELETE FROM ai_recommendations;
DELETE FROM events;
DELETE FROM audit_logs;
DELETE FROM api_keys;
DELETE FROM integrations;
DELETE FROM storage_targets;
DELETE FROM async_tasks;
DELETE FROM team_members WHERE user_id != 1;
DELETE FROM servers;

-- Keep user 1 (Xuan Huang), remove others
DELETE FROM users WHERE id != 1;
