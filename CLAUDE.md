# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ChronoVault is a server backup and recovery platform ("Time Machine for servers") with three components:
- **Backend** (`backend/`): Spring Boot 3.2.5 / Java 17 REST API
- **Frontend** (`frontend/`): Vue 3.5 / TypeScript / Vite 8 / Tailwind CSS 4 SPA
- **Agent** (`agent/`): Go 1.22 CLI daemon for target server operations

## Common Commands

### Backend
```bash
cd backend

# Start infrastructure (PostgreSQL + Redis)
docker-compose up -d

# Run in dev mode (profile: dev)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Compile only (from project root)
mvnw.cmd compile -f backend/pom.xml

# Run all tests (uses H2 in-memory DB, profile: test)
mvnw.cmd test -f backend/pom.xml

# Run a single test class
mvnw.cmd test -f backend/pom.xml -Dtest=SshConnectionManagerTest

# Run tests in a specific package
mvnw.cmd test -f backend/pom.xml -Dtest="com.chronovault.controller.**"
```

### Frontend
```bash
cd frontend

# Dev server (port 5173, proxies /api to localhost:8080)
npm run dev

# Type check
npx vue-tsc --noEmit

# Production build
npm run build
```

### Agent
```bash
cd agent
go build -o chronovault-agent .
./chronovault-agent scan    # Detect server environment (packages, services, ports, docker, configs)
./chronovault-agent run     # Start agent daemon (heartbeat + task polling)
go test ./...               # Run all agent tests
go test -race ./...         # Run with race detector
go vet ./...                # Static analysis
```

## Architecture

### Backend Package Layout (`com.chronovault`)

| Layer | Key Packages | Notes |
|-------|-------------|-------|
| API | `controller/` (28 controllers) | REST endpoints under `/api/`, 206 total endpoints |
| Business | `service/` (15+ services) | Transactional services |
| Data | `entity/` (32 entities), `repository/`, `dto/` | JPA entities, Spring Data repos, DTOs organized by domain |
| Infrastructure | `ssh/`, `snapshot/`, `storage/`, `docker/` | SSH via Apache MINA SSHD, backups via Restic CLI, multi-storage (S3/OSS/WebDAV/LOCAL) |
| Async | `task/AsyncTaskManager`, `task/TaskType` | Thread pool execution with DB-backed task tracking + WebSocket progress |
| AI | `ai/AiClient`, `ai/AiAnalysisService` | OpenAI-compatible API (MiMo v2.5-pro model) |
| Security | `security/`, `config/SecurityConfig` | JWT auth (jjwt), AES-256-GCM credential encryption |
| WebSocket | `websocket/EventWebSocketHandler` | STOMP topics: `/topic/events`, `/topic/tasks`, `/topic/tasks/{id}` |

### Key Patterns

- **SSH Connection Pooling**: `SshConnectionManager` maintains per-server connection pool with idle eviction and stale connection detection (validates with `echo ok` before reuse).
- **Async Tasks**: `AsyncTaskManager.submit()` creates a DB record, executes in `cv-async` thread pool, updates progress via WebSocket. Task types: `SNAPSHOT`, `RECOVER`, `SCAN`.
- **Snapshot Flow**: `SnapshotEngine` → SSH to server → ensure restic installed → `restic init` → pre-hooks (MySQL lock, Redis BGSAVE) → `restic backup` → post-hooks → save manifest.
- **Storage Routing**: `StorageRouter` dispatches to `LocalStorageProvider`, `S3StorageProvider`, `OssStorageProvider`, or `WebDavStorageProvider`. Credentials stored encrypted in `storage_targets.credentials_encrypted`.
- **Restic CLI**: All restic commands run via SSH on target servers. `ResticClient` handles auto-install (curl → apt → yum fallback), dynamic path detection, and exit code 3 (partial success) handling.

### Frontend Structure (`src/`)

- `api/` — 14 Axios modules matching backend controllers. Client in `api/client.ts` auto-attaches JWT, redirects on 401.
- `views/` — 14 page views. Auth guard in `router/index.ts` redirects unauthenticated users to `/login`.
- `components/modals/` — 11 modal dialogs opened via `stores/modal.ts` (Pinia).
- `composables/useWebSocket.ts` — STOMP/SockJS wrapper for real-time subscriptions.
- `stores/` — Pinia stores: `auth` (JWT/login), `modal` (dynamic modals), `toast` (notifications), `app`, `layout`.
- `styles/global.css` — Tailwind 4 `@theme` with Material Design 3 color tokens, glass-morphism components.
- `components/` — Reusable: SkeletonLoader, EmptyState, LoadingSpinner, DiffViewer, StateTree, ToastContainer.

### Database

- PostgreSQL 15, managed by Flyway migrations in `backend/src/main/resources/db/migration/` (V1–V42).
- JPA `ddl-auto: validate` — schema must match migrations exactly. If you modify entity fields, create a new Flyway migration.
- Check latest version: `ls backend/src/main/resources/db/migration/ | sort -V | tail -1`
- Demo data seeded in V12: three users (`xuan@chronovault.io` OWNER, `liwei@chronovault.io` ADMIN, `zhangmin@chronovault.io` MEMBER), all with password `password123`.

#### Core Tables

| Table | Entity | Description |
|-------|--------|-------------|
| `users` | `User` | User accounts with role (OWNER/ADMIN/MEMBER/VIEWER) |
| `servers` | `Server` | Managed servers with SSH config, auto-snapshot, group |
| `snapshots` | `Snapshot` | Backup snapshots with state.json, change_summary, status |
| `snapshot_diffs` | `SnapshotDiff` | Per-field diff between two snapshots |
| `snapshot_tags` | `SnapshotTag` | Tags for snapshots (name + color, unique per snapshot) |
| `snapshot_hooks` | `SnapshotHook` | Pre/post backup hooks (MySQL lock, Redis BGSAVE) |
| `snapshot_manifests` | `SnapshotManifest` | Restic manifest metadata (files_new, bytes_added, etc.) |
| `snapshot_retention_policies` | `SnapshotRetentionPolicy` | Per-server retention rules |
| `storage_targets` | `StorageTarget` | Backup destinations (LOCAL/S3/OSS/WebDAV) with encrypted credentials |
| `alerts` | `Alert` | Generated alerts (severity, category, auto-fix capability) |
| `alert_rules` | `AlertRule` | Configurable alert thresholds and conditions |
| `async_tasks` | `AsyncTask` | Background task tracking (SNAPSHOT/RECOVER/SCAN) with progress |
| `events` | `Event` | System event log (snapshot created, rollback executed, etc.) |
| `audit_logs` | `AuditLog` | User action audit trail with resource type/ID, IP, user-agent |
| `api_keys` | `ApiKey` | API key authentication for Agent communication |
| `agent_info` | `AgentInfo` | Registered agents with version, capabilities, heartbeat |

#### Extended Tables

| Table | Entity | Description |
|-------|--------|-------------|
| `server_groups` | `ServerGroup` | Server grouping (prod/staging/dev) |
| `server_branches` | `ServerBranch` | Git-style branches per server (default + feature branches) |
| `scheduled_backups` | `ScheduledBackup` | Cron-based backup schedules per server |
| `container_states` | `ContainerState` | Docker container snapshots per backup |
| `containers` | `Container` | Monitored container registry |
| `volumes` | `Volume` | Docker volume tracking |
| `ai_insights` | `AiInsight` | AI-generated analysis insights |
| `ai_recommendations` | `AiRecommendation` | AI-generated optimization recommendations |
| `risks` | `Risk` | Detected security/configuration risks |
| `integrations` | `Integration` | External integrations (webhooks, etc.) |
| `webhook_endpoints` | `WebhookEndpoint` | Webhook notification targets |
| `webhook_delivery_logs` | `WebhookDeliveryLog` | Webhook delivery history |
| `team_members` | `TeamMember` | Team membership with roles |
| `disaster_recovery_plans` | `DisasterRecoveryPlan` | DR plan definitions and execution status |
| `verification_jobs` | `VerificationJob` | Backup verification tasks |
| `system_settings` | `SystemSetting` | Application configuration key-value store |

### Environment

Config lives in root `.env` (gitignored). Copy `.env.example` to `.env`.

**Required variables** (backend refuses to start without these):

| Variable | Purpose | Generate |
|----------|---------|----------|
| `JWT_SECRET` | JWT signing (min 32 chars) | `openssl rand -hex 32` |
| `CHRONOVAULT_MASTER_KEY` | AES-256-GCM encryption for SSH credentials | `openssl rand -hex 32` |
| `CHRONOVAULT_RESTIC_PASSWORD` | Restic backup encryption | `openssl rand -hex 32` |

**Optional variables:**

| Variable | Purpose |
|----------|---------|
| `POSTGRES_*` | Database connection (defaults: localhost:5432/chronovault) |
| `REDIS_*` | Redis connection (defaults: localhost:6379) |
| `MIMO_API_KEY` | MiMo AI API key (AI features disabled if empty) |
| `SPRING_PROFILES_ACTIVE` | `dev` (default) or `prod` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins for prod profile |

Backend reads `.env` via `DotenvPostProcessor` (registered in `config/`). Frontend reads `VITE_*` vars via Vite.

### Key Features (Git-style Server Management)

- **Branching**: Create branches from any snapshot to experiment safely. Merge branches back. Default branch tracks main timeline.
- **Stashing**: Temporarily shelve changes (similar to `git stash`). Pop to restore or discard.
- **Bisect**: Binary search through snapshots to find which change introduced a problem. Mark snapshots as good/bad.
- **Cherry-pick**: Extract specific file changes from one server's snapshot and apply to another server.
- **Blame**: See which snapshot last modified each configuration file, who initiated it, and when.
- **Drift Detection**: Compare current server state against last snapshot to detect unauthorized changes.
- **Disaster Recovery**: Define and execute recovery plans. Simulate before executing.
- **Retention Policies**: Per-server rules for automatic snapshot cleanup (keep N days, weekly/monthly).
- **Webhooks**: Notify Slack/DingTalk/Feishu on snapshot events, alerts, and recovery actions.

### Security Model

- **User Roles**: First registered user becomes `OWNER`, all subsequent registrations get `OWNER` → `MEMBER`. Roles: OWNER > ADMIN > MEMBER > VIEWER.
- **Credential Encryption**: SSH keys/passwords encrypted with AES-256-GCM using `CHRONOVAULT_MASTER_KEY`. Stored in `storage_targets.credentials_encrypted` and `servers.ssh_key_encrypted`.
- **SSH Key Verification**: TOFU (Trust On First Use) mode by default. Configure `chronovault.ssh.known-hosts-file` in prod for strict host verification.
- **Terminal Safety**: Dangerous commands blocked (rm -rf /, mkfs, dd, shutdown, etc.).
- **Actuator**: `/actuator/health` public, all other actuator endpoints require authentication.
- **CORS**: Configured via `app.cors.allowed-origins` (both HTTP and WebSocket use same setting).

## Testing

- Backend tests use `application-test.yml` (H2 in-memory DB, Flyway disabled, AI disabled).
- Test profile: `spring.profiles.active=test` (set in test resources).
- **697 tests** across 81 test files covering:
  - **Controllers**: SnapshotController, ServerController, AuthController, AlertController, DashboardController, and more (MockMvc tests)
  - **Services**: Snapshot, Server, Auth, Dashboard, Alert, Storage, Team, Settings, Drift, File, Recovery, Risk, ScheduledBackup, SnapshotTag
  - **Security**: CredentialEncryptor, JwtTokenProvider, SshConnectionManager
  - **Diff**: StateDiffEngine (including edge cases: empty state.json, missing fields, large payloads)
  - **Docker**: DockerOperationService
  - **Async**: AsyncTaskManager
  - **Integration**: Testcontainers-based (PostgreSQL + Redis) for auth flow, snapshot creation, alerts, batch ops, WebSocket

### Running Tests

```bash
# All tests (from project root)
mvnw.cmd test -f backend/pom.xml

# Single test class
mvnw.cmd test -f backend/pom.xml -Dtest=SnapshotServiceTest

# Test package
mvnw.cmd test -f backend/pom.xml -Dtest="com.chronovault.service.**"

# Skip integration tests (faster)
mvnw.cmd test -f backend/pom.xml -Dtest="!*IntegrationTest"
```

### Frontend Testing

```bash
cd frontend
npm run test          # Vitest unit tests
npm run test:e2e      # Playwright E2E tests
```

## API Endpoints (206 total)

Key controllers and their endpoints:

| Controller | Base Path | Key Endpoints |
|-----------|-----------|---------------|
| `AuthController` | `/api/auth` | login, register, refresh, profile, change-password |
| `ServerController` | `/api/servers` | CRUD, SSH connect, clone, volumes, containers, logs |
| `SnapshotController` | `/api/snapshots` | create, list (paged), diff, rollback, selective-restore, batch ops, export, verify |
| `StorageController` | `/api/storage` | CRUD targets, health, distribution, overview |
| `AlertController` | `/api/alerts` | list, acknowledge, rules CRUD, stats |
| `DashboardController` | `/api/dashboard` | overview, stats, topology, activity-trend |
| `ScheduledBackupController` | `/api/scheduled-backups` | CRUD cron schedules, run-now, toggle |
| `SnapshotTagController` | `/api/snapshot-tags` | CRUD tags |
| `ServerBranchController` | `/api/servers/{id}/branches` | create, merge, rename, delete branches |
| `ServerStashController` | `/api/servers/{id}/stash` | create, pop, list, discard stashes |
| `ChangeAttributionController` | `/api/blame` | file blame/attribution history |
| `DriftDetectionController` | `/api/drift` | detect drift between snapshots |
| `RecoveryController` | `/api/recovery` | execute recovery, simulate, migrate |
| `DisasterRecoveryPlanController` | `/api/disaster-recovery` | CRUD plans, execute, status |
| `RiskController` | `/api/risks` | list, score, trend |
| `AiController` | `/api/ai` | insights, recommendations, analysis, predictions |
| `IntegrationController` | `/api/integrations` | CRUD integrations, test |
| `WebhookController` | `/api/webhooks` | endpoints CRUD, delivery logs |
| `TerminalController` | `/api/terminal` | execute commands on servers |
| `AgentController` | `/api/agent` | register, heartbeat, task-result, pending-tasks |
| `SettingsController` | `/api/settings` | API keys, audit logs, AI config |
| `TeamController` | `/api/team` | members CRUD, invite |
| `RetentionPolicyController` | `/api/retention-policies` | CRUD retention rules |
| `TaskController` | `/api/tasks` | list, cancel async tasks |
| `FileController` | `/api/files` | browse server files |
| `VerificationJobController` | `/api/verification` | create, list verification jobs |
| `SnapshotHookController` | `/api/snapshot-hooks` | CRUD pre/post hooks |

---

## Production Deployment

Set `SPRING_PROFILES_ACTIVE=prod` to use `application-prod.yml`. Key differences from dev:
- No default values for secrets — all must be provided via env vars
- JPA `show-sql: false`
- Logging level: `INFO` (not `DEBUG`)
- CORS must be explicitly configured via `CORS_ALLOWED_ORIGINS`
- SSH known-hosts file recommended: `SSH_KNOWN_HOSTS_FILE=/etc/chronovault/known_hosts`
- Actuator health check public, metrics/info require auth

## Autonomous Development (Ralph)

When running in autonomous loop mode (Ralph), follow `.ralph/PROMPT.md` and `.ralph/fix_plan.md` for task priorities.

**Critical rules for autonomous mode:**
1. After each task, run `mvn compile -f backend/pom.xml` (from project root) and `cd frontend && npx vue-tsc --noEmit` to verify compilation
2. After each task, run `mvn test -f backend/pom.xml` to verify no regressions
3. Git commit after each completed task with conventional commit format
4. If a task fails compilation or tests, fix it before moving to the next task
5. Do NOT modify `.ralph/` or `.ralphrc` files
6. When creating Flyway migrations, check the latest version number first: `ls backend/src/main/resources/db/migration/ | sort -V | tail -1`
7. When creating new Java files, match the existing code style: Lombok annotations, `@Slf4j` for logging, constructor injection via `@RequiredArgsConstructor`
8. When creating new Vue components, match existing style: Tailwind CSS 4 with Material Design 3 tokens, `material-symbols-outlined` icons, glass-panel class
