-- V19: Refresh seed data timestamps to be recent
-- This ensures dashboard charts and activity trends show current data

-- Update events to be recent
UPDATE events SET created_at = NOW() - INTERVAL '2 minutes' WHERE id = 1;
UPDATE events SET created_at = NOW() - INTERVAL '3 minutes' WHERE id = 2;
UPDATE events SET created_at = NOW() - INTERVAL '5 minutes' WHERE id = 3;
UPDATE events SET created_at = NOW() - INTERVAL '8 minutes' WHERE id = 4;
UPDATE events SET created_at = NOW() - INTERVAL '12 minutes' WHERE id = 5;

-- Insert additional events spread across recent days for activity trend
INSERT INTO events (user_id, level, message, source, created_at) VALUES
(1, 'INFO', 'Snapshot completed: Prod-East-01', 'snapshot', NOW() - INTERVAL '1 day'),
(1, 'INFO', 'Snapshot completed: Prod-West-02', 'snapshot', NOW() - INTERVAL '1 day 2 hours'),
(NULL, 'WARN', 'High memory usage detected on Node-03', 'alert', NOW() - INTERVAL '2 days'),
(1, 'INFO', 'Backup policy updated', 'snapshot', NOW() - INTERVAL '2 days 3 hours'),
(NULL, 'INFO', 'Container health check passed', 'system', NOW() - INTERVAL '3 days'),
(1, 'INFO', 'Snapshot completed: Staging-01', 'snapshot', NOW() - INTERVAL '3 days 5 hours'),
(NULL, 'WARN', 'Disk usage alert resolved', 'alert', NOW() - INTERVAL '4 days'),
(1, 'INFO', 'New server added: Node-05', 'system', NOW() - INTERVAL '5 days');

-- Update alerts to be recent
UPDATE alerts SET created_at = NOW() - INTERVAL '3 minutes' WHERE id = 1;
UPDATE alerts SET created_at = NOW() - INTERVAL '15 minutes' WHERE id = 2;
UPDATE alerts SET created_at = NOW() - INTERVAL '30 minutes' WHERE id = 4;
UPDATE alerts SET created_at = NOW() - INTERVAL '1 hour' WHERE id = 3;
UPDATE alerts SET created_at = NOW() - INTERVAL '2 hours' WHERE id = 5;

-- Update risks to be recent
UPDATE risks SET discovered_at = NOW() - INTERVAL '2 hours' WHERE id = 1;
UPDATE risks SET discovered_at = NOW() - INTERVAL '15 minutes' WHERE id = 2;
UPDATE risks SET discovered_at = NOW() - INTERVAL '45 minutes' WHERE id = 3;

-- Update audit logs to be recent
UPDATE audit_logs SET created_at = NOW() - INTERVAL '2 minutes' WHERE id = 1;
UPDATE audit_logs SET created_at = NOW() - INTERVAL '15 minutes' WHERE id = 2;
UPDATE audit_logs SET created_at = NOW() - INTERVAL '1 hour' WHERE id = 3;
UPDATE audit_logs SET created_at = NOW() - INTERVAL '3 days' WHERE id = 4;

-- Update team member last active times
UPDATE team_members SET last_active_at = NOW() WHERE id = 1;
UPDATE team_members SET last_active_at = NOW() - INTERVAL '5 minutes' WHERE id = 2;
UPDATE team_members SET last_active_at = NOW() - INTERVAL '2 days' WHERE id = 3;

-- Update user last active times
UPDATE users SET last_active_at = NOW() WHERE id = 1;
UPDATE users SET last_active_at = NOW() - INTERVAL '5 minutes' WHERE id = 2;
UPDATE users SET last_active_at = NOW() - INTERVAL '2 days' WHERE id = 3;

-- Update snapshot timestamps to be recent
UPDATE snapshots SET created_at = NOW() - INTERVAL '12 hours' WHERE id = 1;
UPDATE snapshots SET created_at = NOW() - INTERVAL '1 day' WHERE id = 2;
UPDATE snapshots SET created_at = NOW() - INTERVAL '3 days' WHERE id = 3;
UPDATE snapshots SET created_at = NOW() - INTERVAL '6 hours' WHERE id = 4;
UPDATE snapshots SET created_at = NOW() - INTERVAL '2 days' WHERE id = 5;
UPDATE snapshots SET created_at = NOW() - INTERVAL '4 days' WHERE id = 6;
UPDATE snapshots SET created_at = NOW() - INTERVAL '5 days' WHERE id = 7;
UPDATE snapshots SET created_at = NOW() - INTERVAL '1 hour' WHERE id = 8;
UPDATE snapshots SET created_at = NOW() - INTERVAL '30 minutes' WHERE id = 9;
UPDATE snapshots SET created_at = NOW() - INTERVAL '10 hours' WHERE id = 10;
