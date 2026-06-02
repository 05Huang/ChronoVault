# Changelog

All notable changes to ChronoVault will be documented in this file.

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