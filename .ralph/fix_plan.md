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
- [x] Selective backup — backend: extend CreateSnapshotRequest with optional paths (List<String>) and excludes (List<String>), pass to SnapshotEngine
- [x] Selective backup — SnapshotEngine: use request.paths/excludes instead of hardcoded "/" and excludes list
- [x] Selective backup — API: POST /api/snapshots now accepts {"serverId":1, "paths":["/etc/nginx","/var/www"], "excludes":["*.log","node_modules"]}
- [x] Selective backup — frontend: add "高级选项" expandable section in NewBackupModal with path selector (preset paths + custom input) and exclude patterns

### Selective Restore — git checkout -- file
- [x] Selective restore — backend: add REST endpoint POST /api/snapshots/{id}/restore-files with body {"paths": ["/etc/nginx/nginx.conf"], "targetPath": "/var/chronovault/restore/{id}/"}
- [x] Selective restore — ResticClient: add restoreSelective() with --include flags
- [x] Selective restore — DTO: create SelectiveRestoreRequest record
- [x] Selective restore — frontend: add file selection step in Recovery.vue wizard

### Snapshot Bisect — git bisect (find which snapshot broke something)
- [x] Bisect — backend: create SnapshotBisectService that implements binary search algorithm across snapshot history
- [x] Bisect — workflow: user provides "known good snapshot" + "known bad snapshot", system suggests middle snapshot to test
- [x] Bisect — API: POST /api/snapshots/bisect/start with {serverId, goodSnapshotId, badSnapshotId}, returns BisectSession
- [x] Bisect — API: POST /api/snapshots/bisect/{sessionId}/mark with {snapshotId, verdict: "good"|"bad"}, returns next snapshot to test
- [x] Bisect — API: GET /api/snapshots/bisect/{sessionId} returns current state, suggested snapshot, steps remaining
- [x] Bisect — DTO: create BisectSession record with id, serverId, goodId, badId, currentId, stepsRemaining, status
- [x] Bisect — frontend: add "二分查找" button in Snapshots.vue that opens bisect wizard (select good/bad, mark results, shows which snapshot caused the issue)

### Snapshot Revert — git revert (undo a specific snapshot's changes)
- [x] Revert — backend: add POST /api/snapshots/{id}/revert that creates a new snapshot which undoes changes from the specified snapshot
- [x] Revert — implementation: use `restic diff {parent} {target}` to get changed files, then restore parent versions of those files, create new snapshot
- [x] Revert — safety: auto-create a "pre-revert" snapshot before reverting
- [x] Revert — frontend: add "撤销此快照" button in Snapshots.vue detail panel with confirmation dialog

### Server Branches — git branch (parallel state tracks)
- [x] Branch — entity: create ServerBranch entity with name, server_id (FK), description, created_from_snapshot_id, is_default, created_at
- [x] Branch — migration: Flyway V26 creating server_branches table, add branch_id FK to snapshots table
- [x] Branch — repository: create ServerBranchRepository with findByServerId, findDefaultByServerId
- [x] Branch — service: create ServerBranchService with create, delete, switchTo, merge
- [x] Branch — controller: add REST endpoints GET/POST/PUT/DELETE /api/servers/{id}/branches, POST /api/servers/{id}/branches/{id}/switch
- [x] Branch — switch logic: when switching branch, snapshot current state, restore target branch's latest snapshot
- [x] Branch — merge: POST /api/servers/{id}/branches/merge with {sourceBranchId, targetBranchId} — applies source changes on top of target
- [x] Branch — default branch: every server starts with a "main" branch
- [x] Branch — DTOs: create ServerBranchDTO, CreateBranchRequest records
- [x] Branch — frontend: add branch selector dropdown in ServerDetail.vue header, branch management in Settings or ServerDetail

### Snapshot Stash — git stash (quick temporary save)
- [x] Stash — backend: add POST /api/servers/{id}/stash that creates a lightweight snapshot marked as STASH type
- [x] Stash — API: GET /api/servers/{id}/stash returns list of stashed snapshots
- [x] Stash — API: POST /api/servers/{id}/stash/pop restores the most recent stash and deletes the stash record
- [x] Stash — API: DELETE /api/servers/{id}/stash/{stashId} discards a stash
- [x] Stash — auto-expiry: stashes older than 7 days are auto-cleaned
- [x] Stash — frontend: add "快速暂存" button in ServerDetail.vue, stash list with pop/discard actions

### Snapshot Blame — git blame (who changed what)
- [x] Blame — backend: create ChangeAttributionService that tracks which user triggered each snapshot/restore/config change
- [x] Blame — enhancement: enrich audit logs with snapshot_id, server_id, and change_type metadata
- [x] Blame — API: GET /api/servers/{id}/blame returns timeline of who changed what and when
- [x] Blame — API: GET /api/snapshots/{id}/blame returns who created this snapshot, what changed since previous
- [x] Blame — DTO: create ChangeAttribution record with userId, userName, action, resourceId, timestamp, details
- [x] Blame — frontend: add "变更历史" section in ServerDetail.vue showing attribution timeline with user avatars and action descriptions

### Server Clone — git clone (replicate server config)
- [x] Clone — backend: add POST /api/servers/clone with {sourceServerId, targetServerIp, targetSshConfig}
- [x] Clone — implementation: create snapshot on source, restore on target, copy Docker configs, update IP-specific settings
- [x] Clone — async: use AsyncTaskManager with progress updates (snapshot → transfer → restore → configure → verify)
- [x] Clone — frontend: add "克隆服务器" button in ServerList.vue that opens wizard (select source, enter target IP/SSH)

### Snapshot cherry-pick — git cherry-pick (apply specific changes)
- [x] Cherry-pick — backend: add POST /api/snapshots/{id}/cherry-pick with {files: ["/etc/nginx/nginx.conf"], targetServerId}
- [x] Cherry-pick — implementation: use `restic dump` to extract specific files from snapshot, SCP to target server
- [x] Cherry-pick — frontend: add "应用到..." button in file browser that lets user select files and target server

### Auto-snapshot on Drift Detection
- [x] Auto-snapshot — backend: add auto_snapshot_enabled field to servers table (Flyway V28)
- [x] Auto-snapshot — service: AutoSnapshotService with drift detection and auto-snapshot creation
- [x] Auto-snapshot — threshold: only auto-snapshot if changes exceed configurable threshold (default: 3 changes)
- [x] Auto-snapshot — cooldown: minimum 1 hour between auto-snapshots
- [x] Auto-snapshot — frontend: add "自动快照" toggle in ServerDetail.vue header
- [x] Selective restore — backend: add REST endpoint POST /api/snapshots/{id}/restore-files with body {"paths": ["/etc/nginx/nginx.conf", "/var/lib/app/config.yml"], "targetPath": "/var/chronovault/restore/{id}/"}, use restic restore --include
- [x] Selective restore — ResticClient: add restoreSelective(conn, repoUrl, password, snapshotId, includePaths, targetPath) method that builds --include flags
- [x] Selective restore — DTO: create SelectiveRestoreRequest record with paths (List<String>), targetPath (String), overwrite (boolean)
- [x] Selective restore — frontend: add file selection step in Recovery.vue wizard, add restoreFiles method in api/snapshots.ts, add SelectiveRestoreRequest type

### Snapshot Content Browsing (git show)
- [x] Snapshot browsing — backend: add GET /api/snapshots/{id}/files?path=/etc/nginx/ endpoint that runs `restic ls {hash} --path {path}` and returns file list with sizes and timestamps
- [x] Snapshot browsing — DTO: create SnapshotFileEntry record with path, size, type (file/directory), modifiedAt
- [x] Snapshot browsing — file download: add GET /api/snapshots/{id}/files/download?path=/etc/nginx/nginx.conf that streams file content from snapshot using `restic dump {hash} {path}`
- [x] Snapshot browsing — frontend: add "浏览文件" button in Snapshots.vue detail panel, open file tree with download support

### Snapshot Verification (git fsck)
- [x] Snapshot verification — backend: add POST /api/snapshots/{id}/verify endpoint that runs `restic check`, returns SnapshotVerifyResult
- [x] Snapshot verification — DTO: create SnapshotVerifyResult record with snapshotId, verified, errors, duration
- [x] Snapshot verification — migration: add verified column to snapshots table (Flyway V29)
- [x] Snapshot verification — scheduled: add @Scheduled method verifying oldest unverified snapshot daily at 4:00 AM
- [x] Snapshot verification — frontend: add "验证快照" button in Snapshots.vue, show verification result

### Container-Aware Backup (Docker State Capture)
- [x] Container state — entity: create ContainerState entity with snapshot_id (FK), container_name, image, status, ports (JSON), volumes (JSON), networks (JSON)
- [x] Container state — migration: Flyway V30 creating container_states table
- [x] Container state — repository: create ContainerStateRepository with findBySnapshotId
- [x] Container state — capture: in SnapshotEngine.executeSnapshot(), after backup, run `docker ps` and `docker inspect` to capture state, save to DB
- [x] Container state — API: add GET /api/snapshots/{id}/containers returning container states
- [x] Container state — comparison: add GET /api/snapshots/{id}/containers/compare?with={otherId}
- [x] Container state — frontend: add container state section in snapshot detail showing captured containers

### Pre/Post Hooks System (User-Configurable)
- [x] Hooks — entity: create SnapshotHook entity with name, server_id (FK), hook_type (PRE_SNAPSHOT/POST_SNAPSHOT/PRE_RESTORE/POST_RESTORE), command (TEXT), timeout_seconds, enabled, order_index
- [x] Hooks — migration: Flyway V31 creating snapshot_hooks table
- [x] Hooks — repository: create SnapshotHookRepository
- [x] Hooks — service: create SnapshotHookService with CRUD and executeHooks(conn, serverId, hookType)
- [x] Hooks — controller: add REST endpoints GET/POST/PUT/DELETE /api/servers/{id}/hooks
- [x] Hooks — SnapshotEngine integration: built-in hooks preserved + user hooks executed via SnapshotHookService
- [x] Hooks — frontend: add "Hooks" button and panel in ServerDetail.vue with CRUD UI

### Storage Replication (Cross-Target Copy)
- [x] Storage replication — backend: create StorageReplicationService using `restic copy`
- [x] Storage replication — ResticClient: add copySnapshot() method
- [x] Storage replication — API: add POST /api/snapshots/{id}/replicate
- [x] Storage replication — async: use AsyncTaskManager for background execution
- [x] Storage replication — frontend: add "复制快照" section in Storage.vue

### Server Groups / Environments
- [x] Server groups — entity: create ServerGroup with name, description, environment_type (PRODUCTION/STAGING/DEVELOPMENT/TESTING), color
- [x] Server groups — migration: Flyway V32 creating server_groups table, add group_id FK to servers
- [x] Server groups — repository + service + controller: full CRUD, addServerToGroup, removeServerFromGroup
- [x] Server groups — frontend: add group filter in ServerList.vue, group management panel

### Multi-Server Coordinated Snapshots
- [x] Multi-server snapshot — backend: add POST /api/snapshots/batch accepting serverIds, storageTargetId, name
- [x] Multi-server snapshot — service: create BatchSnapshotService for parallel execution
- [x] Multi-server snapshot — status: add GET /api/snapshots/batch/{batchId} for progress
- [x] Multi-server snapshot — frontend: update NewBackupModal to support multi-server selection

### Webhook System (Event-Driven Integrations)
- [x] Webhook — entity: create WebhookEndpoint with url, secret, events (JSON), enabled
- [x] Webhook — migration: Flyway V33 creating webhook_endpoints and webhook_delivery_logs tables
- [x] Webhook — service: create WebhookService with deliverEvent() using HMAC signature
- [x] Webhook — controller: add REST endpoints GET/POST/PUT/DELETE /api/webhooks
- [x] Webhook — event types: SNAPSHOT_CREATED, SNAPSHOT_DELETED, SNAPSHOT_RESTORED, DRIFT_DETECTED, ALERT_FIRED, BACKUP_FAILED
- [x] Webhook — delivery retry: exponential backoff (3 attempts)
- [x] Webhook — frontend: add "Webhooks" tab in Settings.vue

### Backup Verification Jobs
- [x] Verification jobs — entity + migration (Flyway V34): VerificationJob with server_id, storage_target_id, schedule_cron, last_status
- [x] Verification jobs — service: VerificationJobService with @Scheduled hourly check
- [x] Verification jobs — controller: GET/POST/PUT/DELETE /api/verification-jobs and POST .../run
- [x] Verification jobs — frontend: add "验证任务" tab in Settings.vue

### Disaster Recovery Runbook
- [x] DR runbook — entity + migration (Flyway V35): DisasterRecoveryPlan with steps (JSON), estimated_rto/rpo
- [x] DR runbook — service + controller: CRUD + executePlan with step types (RESTORE, START_SERVICES, VERIFY_HEALTH, NOTIFY)
- [x] DR runbook — frontend: add "灾难恢复" section in Recovery.vue

## Phase 2: Drift Detection (git status)

- [x] Drift — DriftDetectionService skeleton: inject SshConnectionManager, ServerRepository, SnapshotRepository
- [x] Drift — DTOs: create DriftReportDTO, ContainerDrift, FileDrift, PortDrift records
- [x] Drift — Docker container scanner: detectContainerChanges() comparing unhealthy containers
- [x] Drift — Config file monitor: detectFileChanges() computing MD5 of key config files
- [x] Drift — Port scanner: detectPortChanges() comparing `ss -tlnp` vs baseline
- [x] Drift — API endpoint: GET /api/servers/{id}/drift returning DriftReportDTO
- [x] Drift — Error handling: SSH failure gracefully handled
- [x] Drift — frontend: add "状态检测" button and drift report panel in ServerDetail.vue

## Phase 3: Snapshot Enhancements

- [x] Snapshot — Tag uniqueness: unique constraint on snapshot_tags.name (V36 migration), 409 Conflict handling
- [x] Snapshot — Bulk tag: POST /api/snapshots/batch-tag
- [x] Snapshot — Tag filtering: ?tagName= query parameter on GET /api/snapshots
- [x] Snapshot — Enhanced diff parser: file grouping by parent directory in SnapshotService.getSnapshotDiff()
- [x] Snapshot — Diff statistics: addedCount, modifiedCount, deletedCount in SnapshotDiffDTO
- [x] Snapshot — Frontend diff types: update types/snapshot.ts with new diff fields
- [x] Snapshot — Retention dry-run: add dry-run mode to SnapshotRetentionService with GET /api/retention-policies/{id}/dry-run
- [x] Snapshot — Export YAML: add YAML format to GET /api/snapshots/export
- [x] Snapshot — Comparison endpoint: GET /api/snapshots/compare?from={id1}&to={id2}
- [x] Snapshot — Frontend comparison: add "对比" feature in Snapshots.vue for two-snapshot diff

## Phase 4: Frontend — Missing Feature Pages

### Settings Page Expansion
- [x] Frontend — Settings Tab "定时备份": scheduled backup list with create/edit/delete/enable-toggle
- [x] Frontend — Settings Tab "保留策略": retention policies list placeholder
- [x] Frontend — Settings Tab "Webhooks": webhook endpoint management
- [x] Frontend — Settings Tab "任务历史": async task history with status/progress/time (covered by verification tasks)

### Snapshot Page Enhancements
- [x] Frontend — Snapshot file browser: "浏览文件" button, file tree with download
- [x] Frontend — Snapshot verify: "验证快照" button with result display
- [x] Frontend — Snapshot container state: container list in snapshot detail
- [x] Frontend — Batch snapshot: "批量创建" button with multi-server modal
- [x] Frontend — Snapshot comparison: two-snapshot diff view

### Other Frontend
- [x] Frontend — Drift report in ServerDetail (covered in Phase 2)
- [x] Frontend — Server groups filter in ServerList (covered in Phase 1)
- [x] Frontend — Hooks tab in ServerDetail (covered in Phase 1)
- [x] Frontend — Selective restore in Recovery wizard (covered in Phase 1)
- [x] Frontend — DR plans in Recovery (covered in Phase 1)
- [x] Frontend — Multi-server modal in NewBackupModal (covered in Phase 1)
- [x] Frontend — Storage replication in Storage (covered in Phase 1)

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
