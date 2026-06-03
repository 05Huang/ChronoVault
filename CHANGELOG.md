# Changelog

All notable changes to ChronoVault will be documented in this file.

## [0.6.0] - 2026-06-03

### 🎉 State-Aware Snapshots — Core Differentiator

This release introduces ChronoVault's core differentiator: **state-aware snapshots**.
Unlike traditional backup tools, ChronoVault captures not just files but the entire system state
(packages, services, ports, Docker containers, config hashes, crontab) and provides
Git-style diff and selective rollback.

### ✨ New Features

#### State Collection (Agent)
- **Package Detection**: apt/dpkg, rpm/yum, apk with auto-detection
- **Service Monitoring**: systemd services with status, enabled state, PID
- **Port Scanning**: Open ports via ss/netstat with process association
- **Docker State**: Container list, images, ports, compose file discovery
- **Config Hashing**: SHA-256 for /etc/nginx, /etc/mysql, /etc/redis, /etc/ssh, /etc/hosts
- **Crontab Capture**: System and user crontab entries

#### Diff Engine (Backend)
- `StateDiffEngine`: Compares state.json between any two snapshots
- Categorized diffs: packages (added/removed/upgraded), services, ports, Docker, configs
- 12 unit tests covering all diff types
- API: `GET /api/snapshots/state-diff?from={id}&to={id}`

#### Timeline View (Frontend)
- Git-log-style timeline with timestamps and change summary badges
- Each node: timestamp, commit message, change badges (+2 pkgs, -1 svc, ⚠ 3 configs), size
- Click to view details, select two for Diff view
- API: `GET /api/snapshots/timeline?serverId={id}`

#### Diff Visualization (Frontend)
- StateTree component with color-coded changes (green/red/yellow)
- Tabs: packages, services, ports, Docker, configs, crontab
- Summary cards with risk level indicators
- Selective rollback buttons on each change item

#### Selective Rollback
- Roll back individual config files (via Restic dump + SSH write)
- Roll back packages to specific versions (via apt/yum install)
- Roll back services (re-enable disabled services)
- API: `POST /api/snapshots/{id}/rollback/selective`

#### Alert System Enhancement
- Automatic high-risk change detection after each snapshot
- Alert push to Slack, DingTalk, Webhook via `NotificationService`
- Detection: new high-risk ports, service disabling, critical config changes

#### Dashboard Redesign
- Server snapshot staleness indicators (time since last snapshot)
- Recent change summaries with package/service/config counts
- Pending alerts count (high-risk, warnings)
- Single `/api/dashboard/overview` with Redis caching (30s TTL)

### 🔧 Backend Changes

- **39 Flyway migrations** (V37-V39: state.json, JSONB conversion, performance indexes)
- `StateDiffEngine`: 12 unit tests for all diff types
- `StateCollectionService`: SSH-based state collection
- `NotificationService`: Multi-channel alert push
- `DashboardService.getOverview()`: Single API call with caching
- Performance: 7 PostgreSQL indexes, event query limited to 10k
- JWT refresh token mechanism (7-day expiry)
- Auth refresh endpoint: `POST /api/auth/refresh`

### 🖥️ Frontend Changes

- **Timeline.vue**: Git-style timeline with lazy loading
- **SnapshotDiff.vue**: Interactive diff with selective rollback
- **StateTree.vue**: Color-coded state change display
- **Dashboard.vue**: Redesigned with staleness indicators
- System state tab in snapshot details
- JWT refresh token with automatic renewal
- TypeScript types for all API responses
- Route lazy loading for code splitting

### 🤖 Agent Changes

- Full state collection via `CollectStateSnapshot()`
- Packages, services, ports, Docker, configs, crontab
- JSON serialization matching state.json format
- Unit tests for parsing and JSON output

### 🔒 Security

- JWT access token (1h) + refresh token (7d) mechanism
- Automatic token renewal in frontend Axios interceptor
- SSH known_hosts verification (configurable)
- Rate limiting (100 requests/minute)

### 📊 Performance

- PostgreSQL indexes for snapshot list, events, alerts, state.json
- Redis caching for Dashboard overview (30s) and stats (5min)
- Event query limited to 10k records
- Frontend route lazy loading

---

## [0.5.0] - 2026-01-15

### Added
- **Snapshot Bisect**: Find which snapshot introduced a problem using binary search
- **Snapshot Revert**: Undo specific snapshot changes with pre-revert safety snapshot
- **Selective Restore**: Restore specific files from snapshots to any path
- **Snapshot Comparison**: Compare two snapshots with diff statistics
- **Snapshot File Browser**: Browse and download files from snapshot contents
- **Snapshot Verification**: Verify snapshot integrity with restic check
- **Container State Capture**: Capture Docker container state during snapshots
- **Drift Detection**: Monitor container, file, and port changes on servers
- **Server Branches**: Parallel state tracks per server (git branch)
- **Server Clone**: Replicate server configuration to new target
- **Snapshot Cherry-pick**: Apply specific file changes to target server
- **Snapshot Stash**: Quick temporary saves with auto-expiry
- **Auto-snapshot**: Automatic snapshots on drift detection with threshold/cooldown
- **Multi-server Snapshots**: Batch snapshot creation across multiple servers
- **Server Groups**: Organize servers by environment (prod/staging/dev)
- **Storage Replication**: Cross-target backup replication
- **Pre/Post Hooks**: User-configurable automation hooks
- **Webhook System**: Event-driven integrations with HMAC signing
- **Disaster Recovery Runbooks**: Plan-based recovery with RTO/RPO
- **Backup Verification Jobs**: Scheduled backup integrity checks
- **Health Indicators**: SSH and Storage health checks for Actuator
- **Backup Metrics**: Micrometer counters/timers for backup operations
- **Redis Caching**: Dashboard stats with 5-minute TTL
- **Audit Logging**: AOP-based audit trail with IP extraction
- **Security Headers**: XSS, clickjacking, HSTS, CSP protection
- **Password Sanitization**: Credential masking in log output
- **Logback Configuration**: Structured logging with rotation
- **JWT Validation**: WebSocket strict auth with token verification
- **Retry Logic**: Exponential backoff for API requests

### Changed
- All 24 Request DTOs now have Jakarta Validation annotations
- CORS tightened with configurable allowed headers
- WebSocket heartbeat for stale connection detection

### Testing
- Added 15 new test suites with 130+ test cases
- AuthServiceTest (10), SnapshotServiceTest (12), SnapshotTagServiceTest (8)
- ServerServiceTest (18), StorageServiceTest (8), RecoveryServiceTest (8)
- AlertServiceTest (8), ScheduledBackupServiceTest (6), DashboardServiceTest (6)
- TeamServiceTest (6), SettingsServiceTest (6), AsyncTaskManagerTest (10)
- DriftDetectionServiceTest (8), RiskServiceTest (6), UserServiceTest (6)

## [0.4.0] - 2025-12-01

### Added
- Snapshot tagging system
- AI-powered server analysis
- Scheduled backups
- Retention policies
- Alert system with rules
- Integration webhooks (Slack, DingTalk)

## [0.3.0] - 2025-10-15

### Added
- Docker container monitoring
- Volume management
- SSH connection pooling
- Restic backup engine

## [0.2.0] - 2025-09-01

### Added
- Server management
- Basic snapshot/rollback
- User authentication (JWT)

## [0.1.0] - 2025-07-15

### Added
- Initial project setup
- Spring Boot backend
- Vue 3 frontend
- PostgreSQL database
- Basic architecture