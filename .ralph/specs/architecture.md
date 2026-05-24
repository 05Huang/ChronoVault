# ChronoVault — Architecture Reference

## Backend Package Layout (com.chronovault)

| Layer | Package | Purpose |
|-------|---------|---------|
| API | controller/ (17 controllers) | REST endpoints under /api/ |
| Business | service/ (15 services) | Transactional services |
| Data | entity/, repository/, dto/ | JPA entities, Spring Data repos, DTOs |
| Infrastructure | ssh/, snapshot/, storage/, docker/ | SSH (MINA SSHD), Restic CLI, multi-storage |
| Async | task/AsyncTaskManager | Thread pool + DB-backed task tracking + WebSocket |
| AI | ai/AiClient, ai/AiAnalysisService | OpenAI-compatible API (MiMo model) |
| Security | security/, config/SecurityConfig | JWT (jjwt), AES-256-GCM encryption |
| WebSocket | websocket/EventWebSocketHandler | STOMP topics: /topic/events, /topic/tasks |

## Key Patterns

### SSH Connection Pooling
SshConnectionManager maintains per-server connection pool with idle eviction and stale detection.

### Async Tasks
AsyncTaskManager.submit() creates DB record, executes in cv-async thread pool, updates via WebSocket.
Task types: SNAPSHOT, RECOVER, SCAN, MIGRATE.

### Snapshot Flow
SnapshotEngine -> SSH to server -> ensure restic -> restic init -> pre-hooks (MySQL lock, Redis BGSAVE) -> restic backup -> post-hooks -> save manifest.

### Storage Routing
StorageRouter dispatches to LocalStorageProvider, S3StorageProvider, OssStorageProvider, or WebDavStorageProvider.

### Restic CLI
All restic commands run via SSH on target servers. ResticClient handles auto-install (curl -> apt -> yum), dynamic path detection, exit code 3 (partial success).

## Database
- PostgreSQL 15, Flyway migrations V1-V24
- JPA ddl-auto: validate (schema must match migrations)
- Demo data in V12: xuan@chronovault.io (OWNER), liwei@chronovault.io (ADMIN), zhangmin@chronovault.io (MEMBER), all password123

## Frontend Structure (src/)
- api/ — 14 Axios modules, client.ts auto-attaches JWT
- views/ — 14 page views, auth guard in router/index.ts
- components/modals/ — 11 modal dialogs via stores/modal.ts
- composables/useWebSocket.ts — STOMP/SockJS wrapper
- stores/ — Pinia: auth, modal, toast, app, layout

## Security Model
- User roles: OWNER > ADMIN > MEMBER > VIEWER
- Credentials: AES-256-GCM encrypted with CHRONOVAULT_MASTER_KEY
- SSH: TOFU mode by default
- Terminal: dangerous commands blocked (rm -rf /, mkfs, etc.)
- Rate limiting: IP+path sliding window (30/min on auth endpoints)
