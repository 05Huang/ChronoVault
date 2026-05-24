# ChronoVault Fix Plan

## Completed
- [x] Project initialization and basic architecture
- [x] Basic snapshot/rollback/diff functionality
- [x] SSH connection pooling and ResticClient
- [x] Docker containerization and CI/CD
- [x] JWT authentication and role-based access
- [x] Snapshot tagging system: V25 migration, entity, repository, DTO, service, controller
- [x] Snapshot tagging frontend: AddTagModal, tag display on snapshot cards

## Phase 1: Core Product Features — Git for Server State

### Selective Backup — git add (choose what to snapshot)
- [ ] Selective backup — backend: extend CreateSnapshotRequest with optional paths (List<String>) and excludes (List<String>), pass to SnapshotEngine
- [ ] Selective backup — SnapshotEngine: use request.paths/excludes instead of hardcoded "/" and excludes list
- [ ] Selective backup — API: POST /api/snapshots now accepts {"serverId":1, "paths":["/etc/nginx","/var/www"], "excludes":["*.log","node_modules"]}
- [ ] Selective backup — frontend: add "高级选项" expandable section in NewBackupModal with path selector (preset paths + custom input) and exclude patterns

### Selective Restore — git checkout -- file
- [ ] Selective restore — backend: add REST endpoint POST /api/snapshots/{id}/restore-files with body {"paths": ["/etc/nginx/nginx.conf"], "targetPath": "/var/chronovault/restore/{id}/"}
- [ ] Selective restore — ResticClient: add restoreSelective() with --include flags
- [ ] Selective restore — DTO: create SelectiveRestoreRequest record
- [ ] Selective restore — frontend: add file selection step in Recovery.vue wizard

### Snapshot Bisect — git bisect (find which snapshot broke something)
- [ ] Bisect — backend: create SnapshotBisectService that implements binary search algorithm across snapshot history
- [ ] Bisect — workflow: user provides "known good snapshot" + "known bad snapshot", system suggests middle snapshot to test
- [ ] Bisect — API: POST /api/snapshots/bisect/start with {serverId, goodSnapshotId, badSnapshotId}, returns BisectSession
- [ ] Bisect — API: POST /api/snapshots/bisect/{sessionId}/mark with {snapshotId, verdict: "good"|"bad"}, returns next snapshot to test
- [ ] Bisect — API: GET /api/snapshots/bisect/{sessionId} returns current state, suggested snapshot, steps remaining
- [ ] Bisect — DTO: create BisectSession record with id, serverId, goodId, badId, currentId, stepsRemaining, status
- [ ] Bisect — frontend: add "二分查找" button in Snapshots.vue that opens bisect wizard (select good/bad, mark results, shows which snapshot caused the issue)

### Snapshot Revert — git revert (undo a specific snapshot's changes)
- [ ] Revert — backend: add POST /api/snapshots/{id}/revert that creates a new snapshot which undoes changes from the specified snapshot
- [ ] Revert — implementation: use `restic diff {parent} {target}` to get changed files, then restore parent versions of those files, create new snapshot
- [ ] Revert — safety: auto-create a "pre-revert" snapshot before reverting
- [ ] Revert — frontend: add "撤销此快照" button in Snapshots.vue detail panel with confirmation dialog

### Server Branches — git branch (parallel state tracks)
- [ ] Branch — entity: create ServerBranch entity with name, server_id (FK), description, created_from_snapshot_id, is_default, created_at
- [ ] Branch — migration: Flyway V35 creating server_branches table, add branch_id FK to snapshots table
- [ ] Branch — repository: create ServerBranchRepository with findByServerId, findDefaultByServerId
- [ ] Branch — service: create ServerBranchService with create, delete, switchTo, merge
- [ ] Branch — controller: add REST endpoints GET/POST/PUT/DELETE /api/servers/{id}/branches, POST /api/servers/{id}/branches/{id}/switch
- [ ] Branch — switch logic: when switching branch, snapshot current state, restore target branch's latest snapshot
- [ ] Branch — merge: POST /api/servers/{id}/branches/merge with {sourceBranchId, targetBranchId} — applies source changes on top of target
- [ ] Branch — default branch: every server starts with a "main" branch
- [ ] Branch — DTOs: create ServerBranchDTO, CreateBranchRequest records
- [ ] Branch — frontend: add branch selector dropdown in ServerDetail.vue header, branch management in Settings or ServerDetail

### Snapshot Stash — git stash (quick temporary save)
- [ ] Stash — backend: add POST /api/servers/{id}/stash that creates a lightweight snapshot marked as STASH type
- [ ] Stash — API: GET /api/servers/{id}/stash returns list of stashed snapshots
- [ ] Stash — API: POST /api/servers/{id}/stash/pop restores the most recent stash and deletes the stash record
- [ ] Stash — API: DELETE /api/servers/{id}/stash/{stashId} discards a stash
- [ ] Stash — auto-expiry: stashes older than 7 days are auto-cleaned
- [ ] Stash — frontend: add "快速暂存" button in ServerDetail.vue, stash list with pop/discard actions

### Snapshot Blame — git blame (who changed what)
- [ ] Blame — backend: create ChangeAttributionService that tracks which user triggered each snapshot/restore/config change
- [ ] Blame — enhancement: enrich audit logs with snapshot_id, server_id, and change_type metadata
- [ ] Blame — API: GET /api/servers/{id}/blame returns timeline of who changed what and when
- [ ] Blame — API: GET /api/snapshots/{id}/blame returns who created this snapshot, what changed since previous
- [ ] Blame — DTO: create ChangeAttribution record with userId, userName, action, resourceId, timestamp, details
- [ ] Blame — frontend: add "变更历史" section in ServerDetail.vue showing attribution timeline with user avatars and action descriptions

### Server Clone — git clone (replicate server config)
- [ ] Clone — backend: add POST /api/servers/clone with {sourceServerId, targetServerIp, targetSshConfig}
- [ ] Clone — implementation: create snapshot on source, restore on target, copy Docker configs, update IP-specific settings
- [ ] Clone — async: use AsyncTaskManager with progress updates (snapshot → transfer → restore → configure → verify)
- [ ] Clone — frontend: add "克隆服务器" button in ServerList.vue that opens wizard (select source, enter target IP/SSH)

### Snapshot cherry-pick — git cherry-pick (apply specific changes)
- [ ] Cherry-pick — backend: add POST /api/snapshots/{id}/cherry-pick with {files: ["/etc/nginx/nginx.conf"], targetServerId}
- [ ] Cherry-pick — implementation: use `restic dump` to extract specific files from snapshot, SCP to target server
- [ ] Cherry-pick — frontend: add "应用到..." button in file browser that lets user select files and target server

### Auto-snapshot on Drift Detection
- [ ] Auto-snapshot — backend: add auto_snapshot_enabled field to servers table (Flyway V36)
- [ ] Auto-snapshot — service: when DriftDetectionService detects changes, if auto_snapshot_enabled, auto-create snapshot with note "Auto-snapshot: N changes detected"
- [ ] Auto-snapshot — threshold: only auto-snapshot if changes exceed configurable threshold (default: 3 changes)
- [ ] Auto-snapshot — cooldown: minimum 1 hour between auto-snapshots
- [ ] Auto-snapshot — frontend: add "自动快照" toggle in ServerDetail.vue settings
- [ ] Selective restore — backend: add REST endpoint POST /api/snapshots/{id}/restore-files with body {"paths": ["/etc/nginx/nginx.conf", "/var/lib/app/config.yml"], "targetPath": "/var/chronovault/restore/{id}/"}, use restic restore --include
- [ ] Selective restore — ResticClient: add restoreSelective(conn, repoUrl, password, snapshotId, includePaths, targetPath) method that builds --include flags
- [ ] Selective restore — DTO: create SelectiveRestoreRequest record with paths (List<String>), targetPath (String), overwrite (boolean)
- [ ] Selective restore — frontend: add file selection step in Recovery.vue wizard, add restoreFiles method in api/snapshots.ts, add SelectiveRestoreRequest type

### Snapshot Content Browsing (git show)
- [ ] Snapshot browsing — backend: add GET /api/snapshots/{id}/files?path=/etc/nginx/ endpoint that runs `restic ls {hash} --path {path}` and returns file list with sizes and timestamps
- [ ] Snapshot browsing — DTO: create SnapshotFileEntry record with path, size, type (file/directory), modifiedAt
- [ ] Snapshot browsing — file download: add GET /api/snapshots/{id}/files/download?path=/etc/nginx/nginx.conf that streams file content from snapshot using `restic dump {hash} {path}`
- [ ] Snapshot browsing — frontend: add "浏览文件" button in Snapshots.vue detail panel, open file tree with download support

### Snapshot Verification (git fsck)
- [ ] Snapshot verification — backend: add POST /api/snapshots/{id}/verify endpoint that runs `restic check --read-data`, returns SnapshotVerifyResult
- [ ] Snapshot verification — DTO: create SnapshotVerifyResult record with snapshotId, verified, errors, checkedPacks, duration
- [ ] Snapshot verification — migration: add verified column to snapshots table (Flyway V28)
- [ ] Snapshot verification — scheduled: add @Scheduled method verifying oldest unverified snapshot daily at 4:00 AM
- [ ] Snapshot verification — frontend: add "验证快照" button in Snapshots.vue, show verification result

### Container-Aware Backup (Docker State Capture)
- [ ] Container state — entity: create ContainerState entity with snapshot_id (FK), container_name, image, status, ports (JSON), volumes (JSON), networks (JSON)
- [ ] Container state — migration: Flyway V29 creating container_states table
- [ ] Container state — repository: create ContainerStateRepository with findBySnapshotId
- [ ] Container state — capture: in SnapshotEngine.executeSnapshot(), before backup, run `docker ps` and `docker inspect` to capture state, save to DB
- [ ] Container state — API: add GET /api/snapshots/{id}/containers returning container states
- [ ] Container state — comparison: add GET /api/snapshots/{id}/containers/compare?with={otherId}
- [ ] Container state — frontend: add container state section in snapshot detail showing captured containers

### Pre/Post Hooks System (User-Configurable)
- [ ] Hooks — entity: create SnapshotHook entity with name, server_id (FK), hook_type (PRE_SNAPSHOT/POST_SNAPSHOT/PRE_RESTORE/POST_RESTORE), command (TEXT), timeout_seconds, enabled, order_index
- [ ] Hooks — migration: Flyway V30 creating snapshot_hooks table
- [ ] Hooks — repository: create SnapshotHookRepository
- [ ] Hooks — service: create SnapshotHookService with CRUD and executeHooks(conn, serverId, hookType)
- [ ] Hooks — controller: add REST endpoints GET/POST/PUT/DELETE /api/servers/{id}/hooks
- [ ] Hooks — SnapshotEngine integration: replace hardcoded hooks with configurable system
- [ ] Hooks — frontend: add "Hooks" tab in ServerDetail.vue with CRUD UI

### Storage Replication (Cross-Target Copy)
- [ ] Storage replication — backend: create StorageReplicationService using `restic copy`
- [ ] Storage replication — ResticClient: add copySnapshot() method
- [ ] Storage replication — API: add POST /api/snapshots/{id}/replicate
- [ ] Storage replication — async: use AsyncTaskManager for background execution
- [ ] Storage replication — frontend: add "复制快照" action in Storage.vue

### Server Groups / Environments
- [ ] Server groups — entity: create ServerGroup with name, description, environment_type (PRODUCTION/STAGING/DEVELOPMENT/TESTING), color
- [ ] Server groups — migration: Flyway V31 creating server_groups table, add group_id FK to servers
- [ ] Server groups — repository + service + controller: full CRUD, addServerToGroup, removeServerFromGroup
- [ ] Server groups — frontend: add group filter in ServerList.vue, group management modal

### Multi-Server Coordinated Snapshots
- [ ] Multi-server snapshot — backend: add POST /api/snapshots/batch accepting serverIds, storageTargetId, name
- [ ] Multi-server snapshot — service: create BatchSnapshotService for parallel execution
- [ ] Multi-server snapshot — status: add GET /api/snapshots/batch/{batchId} for progress
- [ ] Multi-server snapshot — frontend: update NewBackupModal to support multi-server selection

### Webhook System (Event-Driven Integrations)
- [ ] Webhook — entity: create WebhookEndpoint with url, secret, events (JSON), enabled
- [ ] Webhook — migration: Flyway V32 creating webhook_endpoints and webhook_delivery_logs tables
- [ ] Webhook — service: create WebhookService with deliverEvent() using HMAC signature
- [ ] Webhook — controller: add REST endpoints GET/POST/PUT/DELETE /api/webhooks
- [ ] Webhook — event types: SNAPSHOT_CREATED, SNAPSHOT_DELETED, SNAPSHOT_RESTORED, DRIFT_DETECTED, ALERT_FIRED, BACKUP_FAILED
- [ ] Webhook — delivery retry: exponential backoff (3 attempts)
- [ ] Webhook — frontend: add "Webhooks" tab in Settings.vue

### Backup Verification Jobs
- [ ] Verification jobs — entity + migration (Flyway V33): VerificationJob with server_id, storage_target_id, schedule_cron, last_status
- [ ] Verification jobs — service: VerificationJobService with @Scheduled hourly check
- [ ] Verification jobs — controller: GET/POST/DELETE /api/verification-jobs
- [ ] Verification jobs — frontend: add tab in Settings.vue

### Disaster Recovery Runbook
- [ ] DR runbook — entity + migration (Flyway V34): DisasterRecoveryPlan with steps (JSON), estimated_rto/rpo
- [ ] DR runbook — service + controller: CRUD + executePlan with step types (RESTORE, START_SERVICES, VERIFY_HEALTH, NOTIFY)
- [ ] DR runbook — frontend: add "灾难恢复" section in Recovery.vue

## Phase 2: Drift Detection (git status)

- [ ] Drift — DriftDetectionService skeleton: inject SshConnectionManager, ServerRepository, SnapshotRepository
- [ ] Drift — DTOs: create DriftReportDTO, ContainerDrift, FileDrift records
- [ ] Drift — Docker container scanner: detectContainerChanges() comparing `docker ps` vs snapshot manifest
- [ ] Drift — Config file monitor: detectFileChanges() computing MD5 of key config files
- [ ] Drift — Port scanner: detectPortChanges() comparing `ss -tlnp` vs baseline
- [ ] Drift — Baseline storage: add config_hashes and port_snapshot columns to snapshot_manifests (Flyway V26)
- [ ] Drift — API endpoint: GET /api/servers/{id}/drift returning DriftReportDTO
- [ ] Drift — Error handling: SSH failure, no snapshot, partial failure
- [ ] Drift — frontend: add "状态检测" section in ServerDetail.vue with color-coded drift report + "创建快照" button

## Phase 3: Snapshot Enhancements

- [ ] Snapshot — Tag uniqueness: unique constraint on snapshot_tags.name, 409 Conflict handling
- [ ] Snapshot — Bulk tag: POST /api/snapshots/batch-tag
- [ ] Snapshot — Tag filtering: ?tagName= query parameter on GET /api/snapshots
- [ ] Snapshot — Enhanced diff parser: file grouping by parent directory in SnapshotService.getSnapshotDiff()
- [ ] Snapshot — Diff statistics: addedCount, modifiedCount, deletedCount in SnapshotDiffDTO
- [ ] Snapshot — Frontend diff types: update types/snapshot.ts with new diff fields
- [ ] Snapshot — Retention dry-run: add dry-run mode to SnapshotRetentionService
- [ ] Snapshot — Retention audit: add retention_policy_id column to snapshots (Flyway V27)
- [ ] Snapshot — Export YAML: add YAML format to GET /api/snapshots/export
- [ ] Snapshot — Comparison endpoint: GET /api/snapshots/compare?from={id1}&to={id2}
- [ ] Snapshot — Frontend comparison: add "对比" feature in Snapshots.vue for two-snapshot diff

## Phase 4: Frontend — Missing Feature Pages

### Settings Page Expansion
- [ ] Frontend — Settings Tab "定时备份": scheduled backup list with create/edit/delete/enable-toggle
- [ ] Frontend — Settings Tab "保留策略": retention policies list with create/edit/delete
- [ ] Frontend — Settings Tab "Webhooks": webhook endpoint management
- [ ] Frontend — Settings Tab "任务历史": async task history with status/progress/time

### Snapshot Page Enhancements
- [ ] Frontend — Snapshot file browser: "浏览文件" button, file tree with download
- [ ] Frontend — Snapshot verify: "验证快照" button with result display
- [ ] Frontend — Snapshot container state: container list in snapshot detail
- [ ] Frontend — Batch snapshot: "批量创建" button with multi-server modal
- [ ] Frontend — Snapshot comparison: two-snapshot diff view

### Other Frontend
- [ ] Frontend — Drift report in ServerDetail (covered in Phase 2)
- [ ] Frontend — Server groups filter in ServerList (covered in Phase 1)
- [ ] Frontend — Hooks tab in ServerDetail (covered in Phase 1)
- [ ] Frontend — Selective restore in Recovery wizard (covered in Phase 1)
- [ ] Frontend — DR plans in Recovery (covered in Phase 1)
- [ ] Frontend — Multi-server modal in NewBackupModal (covered in Phase 1)
- [ ] Frontend — Storage replication in Storage (covered in Phase 1)

## Phase 5: Agent Improvements

- [ ] Agent — Health check: /health endpoint with restic version, disk space, last scan time
- [ ] Agent — Config validation: fail fast on startup with clear error messages
- [ ] Agent — Graceful shutdown: SIGTERM handling, finish in-progress tasks
- [ ] Agent — Error handling: improve messages for disk full, permission denied, network timeout
- [ ] Agent — Unit tests: add tests for scanner/docker.go, scanner/webserver.go, scanner/system.go
- [ ] Agent — TLS support: verify end-to-end with backend
- [ ] Agent — Retry logic: exponential backoff in transport/client.go

## Phase 6: WebSocket & Real-time

- [ ] WS — Strict auth: validate JWT expiry and user existence in handshake
- [ ] WS — Heartbeat: ping-pong to detect stale connections
- [ ] WS — Event filtering: subscribe to specific event types
- [ ] WS — Connection tracking: expose active connections via /actuator/metrics

## Later Priority — Production Hardening (do these AFTER all features are complete)

### Input Validation
- [ ] Validation — add Jakarta Validation annotations to ALL DTO Request classes
- [ ] Validation — add MethodArgumentNotValidException handler in GlobalExceptionHandler

### Audit Logging
- [ ] Audit — create @Auditable annotation + AuditLogAspect with @AfterReturning
- [ ] Audit — annotate all controllers with @Auditable
- [ ] Audit — IP extraction from request headers

### Logging & Security
- [ ] Logging — logback-spring.xml with dev=console/prod=JSON, file rotation
- [ ] Logging — sanitize passwords/keys in ResticClient, SshConnectionManager, CredentialEncryptor
- [ ] Security — response headers (X-Content-Type-Options, X-Frame-Options, HSTS, CSP)
- [ ] Security — CORS tightening for production

### Test Coverage
- [ ] Test — AuthServiceTest (10 cases)
- [ ] Test — SnapshotServiceTest (12 cases)
- [ ] Test — SnapshotTagServiceTest (8 cases)
- [ ] Test — ServerServiceTest (10 cases)
- [ ] Test — StorageServiceTest (8 cases)
- [ ] Test — RecoveryServiceTest (8 cases)
- [ ] Test — AlertServiceTest (8 cases)
- [ ] Test — ScheduledBackupServiceTest (6 cases)
- [ ] Test — DashboardServiceTest (6 cases)
- [ ] Test — TeamServiceTest (6 cases)
- [ ] Test — SettingsServiceTest (6 cases)
- [ ] Test — AsyncTaskManagerTest (6 cases)
- [ ] Test — DriftDetectionServiceTest (8 cases)
- [ ] Test — RiskServiceTest (6 cases)
- [ ] Test — UserServiceTest (6 cases)

### Observability
- [ ] Health — SshConnectionHealthIndicator + StorageHealthIndicator
- [ ] Metrics — BackupMetrics with Counter/Timer/Gauge
- [ ] Metrics — embed in SnapshotService and RecoveryService

### Performance & Caching
- [ ] Cache — Dashboard stats, server list, storage overview, risk score in Redis
- [ ] DB — index audit, N+1 detection, HikariCP tuning
- [ ] Async — cleanup old AsyncTask and Event records

### API Improvements
- [ ] API — pagination standardization, response wrapper audit
- [ ] API — per-user rate limiting, API key rotation
- [ ] API — password change, profile update endpoints
- [ ] API — Swagger annotations on all controllers

### Documentation
- [ ] Docs — README, CHANGELOG, architecture diagram, deployment guide

## Notes
- After each task: compile check + test check + git commit (MANDATORY)
- Every backend feature MUST have a frontend entry point (Rule 6)
- Maintain visual consistency (Rule 2): glass-panel, MD3 tokens, material-symbols-outlined
- Study Dashboard.vue, ServerList.vue, Snapshots.vue as style reference
- Features first, hardening later — complete Phase 1-6 before touching "Later Priority"
