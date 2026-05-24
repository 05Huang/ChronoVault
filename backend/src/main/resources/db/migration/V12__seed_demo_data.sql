-- V12: Seed demo data for ChronoVault
-- Password for all users: "password123" (BCrypt encoded)

-- Users
INSERT INTO users (name, email, password_hash, role, status, last_active_at) VALUES
('Xuan Huang', 'xuan@chronovault.io', '$2a$10$3GGrIPHplva4fk29R4pyiuO8QeUFhAxrxKsBaypKYp1VF6MDUyCZO', 'OWNER', 'ONLINE', NOW()),
('Li Wei', 'liwei@chronovault.io', '$2a$10$3GGrIPHplva4fk29R4pyiuO8QeUFhAxrxKsBaypKYp1VF6MDUyCZO', 'ADMIN', 'ONLINE', NOW() - INTERVAL '5 minutes'),
('Zhang Min', 'zhangmin@chronovault.io', '$2a$10$3GGrIPHplva4fk29R4pyiuO8QeUFhAxrxKsBaypKYp1VF6MDUyCZO', 'MEMBER', 'OFFLINE', NOW() - INTERVAL '2 days');

-- Servers
INSERT INTO servers (user_id, name, ip, os, status, uptime_seconds) VALUES
(1, 'Prod-East-01', '10.0.42.128', 'Ubuntu 22.04 LTS (HWE)', 'RUNNING', 13478400),
(1, 'Prod-West-02', '10.0.43.64', 'CentOS Stream 9', 'RUNNING', 8640000),
(1, 'Staging-01', '10.0.50.100', 'Debian 12', 'RUNNING', 2592000),
(1, 'Node-03', '10.0.42.130', 'Ubuntu 22.04 LTS', 'RUNNING', 5184000),
(1, 'Node-05', '10.0.42.132', 'Ubuntu 22.04 LTS', 'ERROR', 1728000);

-- Containers for Prod-East-01
INSERT INTO containers (server_id, name, type, cpu_percent, memory_percent, memory_mb, disk_io, status) VALUES
(1, 'Nginx-LB', 'HTTP', 12.4, 45.0, 256, '1.2MB/s', 'RUNNING'),
(1, 'MySQL-Main', 'DATABASE', 45.2, 68.0, 2048, '2.4MB/s', 'RUNNING'),
(1, 'Redis-Store', 'CACHE', 8.1, 32.0, 512, '0.8MB/s', 'RUNNING');

-- Containers for Prod-West-02
INSERT INTO containers (server_id, name, type, cpu_percent, memory_percent, memory_mb, disk_io, status) VALUES
(2, 'API-Gateway', 'HTTP', 22.5, 55.0, 1024, '1.8MB/s', 'RUNNING'),
(2, 'PostgreSQL-01', 'DATABASE', 38.7, 72.0, 4096, '3.2MB/s', 'RUNNING');

-- Volumes for Prod-East-01
INSERT INTO volumes (server_id, name, container_path, host_path, size_bytes, status) VALUES
(1, 'db_data', '/var/lib/mysql', '/mnt/volumes/mysql_data', 13337206784, 'RW'),
(1, '.env.production', '/app/.env', '/etc/chronovault/env', NULL, '安全'),
(1, 'nginx_logs', '/var/log/nginx', '/mnt/volumes/nginx_logs', 4509715456, '7 天过期');

-- Snapshots
INSERT INTO snapshots (server_id, title, description, status, hash, size_bytes, type, note, microservice_count, created_at) VALUES
(1, '部署前自动快照', '系统在部署 v2.4.0 核心组件前生成的完整环境备份。包含 42 个微服务状态。', 'STABLE', '8f2a9c1', 1503238553600, 'FULL', 'Pre-deployment snapshot', 42, NOW() - INTERVAL '1 day'),
(1, '数据库升级前', '手动执行。在升级 PostgreSQL 集群至 v15 之前记录的结构与数据镜像。', 'WARNING', 'b4e7d23', 268435456000, 'FULL', 'Manual pre-upgrade snapshot', 38, NOW() - INTERVAL '2 days'),
(1, '定期例行备份', '每周日例行维护快照。状态：已验证。', 'ARCHIVED', 'a1c3e5f', 107374182400, 'INCREMENTAL', 'Weekly maintenance', 42, NOW() - INTERVAL '3 days'),
(2, 'API 网关配置快照', 'API Gateway v3.2 部署前的完整配置备份。', 'STABLE', 'd2f4a6b', 53687091200, 'FULL', 'Gateway config backup', 12, NOW() - INTERVAL '12 hours');

-- Snapshot diffs
INSERT INTO snapshot_diffs (snapshot_id, file_path, prev_value, next_value) VALUES
(1, '/k8s/deployment.yaml', 'replicas: 3', 'replicas: 12'),
(1, '/env/auth-service', 'TIMEOUT=5s', 'TIMEOUT=30s'),
(1, '/docker/base-img', 'node:18-alpine', 'node:20-alpine');

-- Alerts
INSERT INTO alerts (server_id, severity, title, description, source, category, root_cause_analysis, storage_percent, growth_rate, status, created_at) VALUES
(5, 'CRITICAL', '内存溢出导致容器崩溃 (OOM Kill)', '生产环境集群中 chrono-worker-v2 实例因消耗 32GB RAM 触发内核 OOM-Killer。', 'Node-082 (Tokyo-East)', 'Docker', '// Root Cause Analysis by AI' || E'\n' || '发现循环引用：at src/engine/core.ts:442:10' || E'\n' || 'Heap usage peaked at 98.4% before termination.', NULL, NULL, 'OPEN', NOW() - INTERVAL '3 minutes'),
(NULL, 'PREDICTIVE', '磁盘空间预计在 2 小时内耗尽', '基于当前增长率 (4.2GB/min)，存储卷 vol-8821 将在 UTC 14:30 达到临界值。', 'Database Cluster Alpha', 'DB', NULL, 82, '4.2GB/min', 'OPEN', NOW() - INTERVAL '15 minutes'),
(1, 'WARNING', 'P99 延迟超过 500ms 阈值', '最近 500 次请求中，尾部延迟显著增加，主要集中在 /v1/auth 接口。', 'API Gateway', 'System', NULL, NULL, NULL, 'OPEN', NOW() - INTERVAL '1 hour'),
(1, 'WARNING', 'CPU 使用率持续偏高', 'Node-01 CPU 使用率在过去 30 分钟内持续超过 80%。', 'Prod-East-01', 'System', NULL, NULL, NULL, 'OPEN', NOW() - INTERVAL '30 minutes'),
(1, 'CRITICAL', '数据库连接池耗尽', 'MySQL 连接池已达上限 (max_connections=500)。', 'Node-05 (Tokyo-East)', 'DB', NULL, NULL, NULL, 'RESOLVED', NOW() - INTERVAL '2 hours');

-- Alert rules
INSERT INTO alert_rules (user_id, name, metric, threshold, duration_minutes, severity, enabled) VALUES
(1, 'CPU 使用率过高', 'CPU 使用率', 90, 5, 'CRITICAL', true),
(1, '内存使用率告警', '内存使用率', 85, 10, 'WARNING', true),
(1, '磁盘空间不足', '磁盘使用率', 90, 15, 'CRITICAL', true);

-- Integrations
INSERT INTO integrations (user_id, type, name, url, active) VALUES
(1, 'SLACK', 'Slack', '#alerts-prod', true),
(1, 'EMAIL', 'Email', 'admin@chronovault.io', true),
(1, 'WEBHOOK', 'Webhook', NULL, false);

-- Storage targets
INSERT INTO storage_targets (user_id, type, name, endpoint, used_bytes, total_bytes, status) VALUES
(1, 'S3', 'S3 主存储桶', 'https://s3.amazonaws.com/chronovault-primary', 2576980377600, 8589934592000, 'ACTIVE'),
(1, 'BLOCK', '块存储卷', '/dev/sdb1', 618475290624, 2147483648000, 'ACTIVE'),
(1, 'ARCHIVE', '冷归档 Glacier', 'https://glacier.us-east-1.amazonaws.com', 1181116006400, 5368709120000, 'ACTIVE');

-- Team members
INSERT INTO team_members (owner_id, user_id, email, name, role, permissions, status, last_active_at) VALUES
(1, 1, 'xuan@chronovault.io', 'Xuan Huang', 'OWNER', 'snapshot,recovery,settings,team', 'ONLINE', NOW()),
(1, 2, 'liwei@chronovault.io', 'Li Wei', 'ADMIN', 'snapshot,recovery,settings', 'ONLINE', NOW() - INTERVAL '5 minutes'),
(1, 3, 'zhangmin@chronovault.io', 'Zhang Min', 'MEMBER', 'snapshot', 'OFFLINE', NOW() - INTERVAL '2 days');

-- API keys
INSERT INTO api_keys (user_id, name, prefix, key_hash, scope, created_at) VALUES
(1, 'CI/CD Pipeline', 'cv_prod_9a2f', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'WRITE', '2024-01-15 10:00:00'),
(1, 'Monitoring Agent', 'cv_mon_3b8e', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'READ', '2024-02-20 14:00:00');

-- Audit logs
INSERT INTO audit_logs (user_id, action, icon, ip_address, created_at) VALUES
(1, '用户登录: xuan@chronovault.io', 'login', '192.168.1.100', NOW() - INTERVAL '2 minutes'),
(2, '手动触发快照: Prod-East-01', 'cached', '192.168.1.101', NOW() - INTERVAL '15 minutes'),
(1, '修改备份策略: 每 4 小时 -> 每 1 小时', 'settings', '192.168.1.100', NOW() - INTERVAL '1 hour'),
(2, '邀请新成员: zhangmin@chronovault.io', 'person_add', '192.168.1.101', NOW() - INTERVAL '3 days');

-- Risks
INSERT INTO risks (server_id, level, title, description, category, ai_suggestion, action_text, status, discovered_at) VALUES
(5, 'CRITICAL', 'MySQL 实例未备份 (Node-05)', '检测到 prod-db-cluster-01 在过去 48 小时内未生成任何有效的快照。这可能导致在发生硬件故障时出现数据丢失风险。', '数据一致性', '由于检测到备份 API 连接超时，建议立即手动触发离线快照，并检查 VPC 内网穿透策略。', '执行自动修复', 'OPEN', NOW() - INTERVAL '2 hours'),
(4, 'WARNING', '磁盘使用率激增趋势 (Node-03)', '节点 app-gateway-03 的磁盘占用率在过去 1 小时内从 40% 增长至 82%，预计将在 15 分钟内达到上限。', '基础设施', '日志文件 /var/log/syslog 异常膨胀。建议启用日志轮转压缩或扩容云硬盘。', '清理冗余日志', 'OPEN', NOW() - INTERVAL '15 minutes'),
(NULL, 'ANOMALOUS', '异常 SSH 登录尝试 (Global)', '检测到 522 次来自 IP 192.168.1.105 的失败登录尝试。系统自动锁定了涉及的 Root 账号端口访问权限。', '网络安全', '疑似暴力破解。建议立即将该 IP 加入黑名单，并强制启用全域 MFA 二步验证。', '拉黑该 IP', 'OPEN', NOW() - INTERVAL '45 minutes');

-- AI insights
INSERT INTO ai_insights (title, description, icon, category) VALUES
('备份窗口优化', '检测到凌晨 2-4 点备份窗口与数据库维护窗口重叠，建议错开 30 分钟以避免锁竞争。', 'schedule', '性能优化'),
('存储成本控制', 'S3 存储桶中 40% 的数据超过 90 天未访问，建议迁移至 Glacier 冷归档以节省 60% 成本。', 'savings', '成本控制'),
('安全加固建议', '发现 3 台服务器未启用自动安全更新。建议配置 unattended-upgrades 以减少漏洞暴露窗口。', 'security', '安全状态'),
('数据库性能洞察', 'MySQL 慢查询日志显示 12 条查询超过 2s。建议为 user_sessions 表添加复合索引。', 'speed', '数据库');

-- AI recommendations
INSERT INTO ai_recommendations (title, description, icon, impact, applied) VALUES
('启用 Intelligent-Tiering', 'S3 存储桶当前使用 Standard 存储类别。启用 Intelligent-Tiering 可自动将不频繁访问的数据移至低成本层。', 'auto_awesome', '节省 ¥2.4K/月', false),
('优化备份频率', 'MySQL binlog 分析显示数据变更集中在工作时间。建议工作时间每 30 分钟备份，非工作时间每 2 小时备份。', 'schedule', 'RPO 降低 75%', false),
('清理孤立 Docker 卷', '检测到 23 个未被任何容器引用的 Docker 卷，总计占用 180GB 空间。', 'delete_sweep', '释放 180GB', false);

-- Events
INSERT INTO events (user_id, level, message, source, created_at) VALUES
(1, 'INFO', 'Config updated on <b>Edge-01</b>', 'Edge-01', NOW() - INTERVAL '2 minutes'),
(NULL, 'ERR', 'Redis reconnection failed. Retrying...', 'Redis-Store', NOW() - INTERVAL '3 minutes'),
(1, 'INFO', 'User <b>mark_admin</b> logged in', 'Auth', NOW() - INTERVAL '5 minutes'),
(NULL, 'INFO', 'Daily snapshot successful: vol-snap-99', 'SnapshotEngine', NOW() - INTERVAL '8 minutes'),
(NULL, 'WARN', 'CPU spike detected on K8s cluster node-02', 'K8s-Monitor', NOW() - INTERVAL '12 minutes');
