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

# Compile only
mvn compile

# Run tests (uses H2 in-memory DB, profile: test)
mvn test

# Run a single test class
mvn test -Dtest=SshConnectionManagerTest
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
./chronovault-agent scan    # Detect server environment
./chronovault-agent run     # Start agent daemon
```

## Architecture

### Backend Package Layout (`com.chronovault`)

| Layer | Key Packages | Notes |
|-------|-------------|-------|
| API | `controller/` (17 controllers) | REST endpoints under `/api/` |
| Business | `service/` (15 services) | Transactional services |
| Data | `entity/`, `repository/`, `dto/` | JPA entities, Spring Data repos, DTOs organized by domain |
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

- PostgreSQL 15, managed by Flyway migrations in `backend/src/main/resources/db/migration/` (V1–V40).
- JPA `ddl-auto: validate` — schema must match migrations exactly. If you modify entity fields, create a new Flyway migration.
- Check latest version: `ls backend/src/main/resources/db/migration/ | sort -V | tail -1`
- Demo data seeded in V12: three users (`xuan@chronovault.io` OWNER, `liwei@chronovault.io` ADMIN, `zhangmin@chronovault.io` MEMBER), all with password `password123`.
- Key tables: `servers`, `snapshots`, `snapshot_manifests`, `storage_targets`, `async_tasks`, `events`, `alerts`, `users`.

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
- **287 tests** across 24 test classes: controllers (SnapshotController, ServerController, AuthController), services (Snapshot, Server, Auth, Dashboard, Alert, Storage, Team, Settings, Drift, File, Recovery, Risk, ScheduledBackup, SnapshotTag), security (CredentialEncryptor, SshConnectionManager), diff (StateDiffEngine), docker (DockerOperationService), task (AsyncTaskManager).
- No frontend test suite configured (Vitest setup pending).

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
