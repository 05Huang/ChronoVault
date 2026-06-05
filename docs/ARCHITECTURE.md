# ChronoVault Architecture

> Version: 0.1.0 | Last updated: 2026-06-06

## Table of Contents

- [System Overview](#system-overview)
- [High-Level Architecture](#high-level-architecture)
- [Component Responsibilities](#component-responsibilities)
- [Data Flow](#data-flow)
- [Database Schema](#database-schema)
- [API Design](#api-design)
- [Security Architecture](#security-architecture)
- [Deployment Topology](#deployment-topology)
- [Development Guide](#development-guide)

---

## System Overview

ChronoVault is a **server state version control platform** that enables teams to manage server environments like code — with snapshots, diffs, branches, and rollback.

**Core differentiators vs. Backrest (5.9k stars)**:

| Feature | Backrest | ChronoVault |
|---------|----------|-------------|
| Backup engine | Restic | Restic |
| Multi-server | ❌ Single machine | ✅ Centralized management |
| State snapshots | ❌ Files only | ✅ Packages, services, Docker, configs |
| Diff view | ❌ | ✅ Git-style diff between snapshots |
| Branching | ❌ | ✅ Git-style branches per server |
| Rollback | Full restore | ✅ Selective (files/packages/configs) |

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        User Browser                             │
│                    (Chrome/Firefox/Safari)                       │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTPS / WebSocket (STOMP)
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Frontend (Vue 3 SPA)                          │
│                   Port: 80 (nginx) / 5173 (dev)                 │
│                                                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │Dashboard │ │Snapshots │ │Timeline  │ │  SnapshotDiff     │  │
│  │          │ │          │ │(Git log) │ │  (side-by-side)   │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │Servers   │ │Recovery  │ │Settings  │ │  Alerts/Risks     │  │
│  │          │ │          │ │          │ │                   │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
│                                                                 │
│  State: Pinia stores (auth, modal, toast, app)                 │
│  API:   14 Axios modules → auto-attach JWT, 401 redirect      │
│  WS:    STOMP/SockJS → /topic/events, /topic/tasks/{id}       │
└──────────────────────────┬──────────────────────────────────────┘
                           │ REST API + WebSocket
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                Backend (Spring Boot 3.2.5)                      │
│                Port: 8080                                       │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    API Layer (28 Controllers)            │   │
│  │  Auth│Server│Snapshot│Alert│Dashboard│Storage│AI│...    │   │
│  └──────────────────────┬──────────────────────────────────┘   │
│                          │                                      │
│  ┌──────────────────────┴──────────────────────────────────┐   │
│  │                 Business Layer (35+ Services)            │   │
│  │  SnapshotService│ServerService│AlertService│AiService   │   │
│  │  RecoveryService│DashboardService│...                   │   │
│  └──────────────────────┬──────────────────────────────────┘   │
│                          │                                      │
│  ┌──────────────────────┴──────────────────────────────────┐   │
│  │               Infrastructure Layer                      │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │   │
│  │  │SSH Pool  │ │Snapshot  │ │Storage   │ │State Diff│  │   │
│  │  │(MINA)    │ │Engine    │ │Router    │ │Engine    │  │   │
│  │  │          │ │(Restic)  │ │(S3/OSS/  │ │(JSON)    │  │   │
│  │  │          │ │          │ │Local/Web)│ │          │  │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐               │   │
│  │  │Async Task│ │WebSocket │ │AI Client │               │   │
│  │  │Manager   │ │(STOMP)   │ │(MiMo)    │               │   │
│  │  └──────────┘ └──────────┘ └──────────┘               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Data Layer                            │   │
│  │  PostgreSQL 15 (42 migrations)  │  Redis 7 (cache/pub)  │   │
│  └─────────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP (Bearer Token + HMAC)
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│              Agent (Go 1.22) — per target server                │
│              Port: 8081                                         │
│                                                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │Scanner   │ │Restic    │ │Transport │ │Health    │         │
│  │(state    │ │Client    │ │(HTTP+WS) │ │Endpoint  │         │
│  │.json)    │ │(CLI)     │ │          │ │(/health) │         │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘         │
│                                                                 │
│  Collectors: packages│services│ports│docker│configs│crontab    │
│  Backup: Restic CLI (init│backup│restore│check│forget)         │
└─────────────────────────────────────────────────────────────────┘
```

---

## Component Responsibilities

### Backend Packages (`com.chronovault`)

| Package | Responsibility | Key Classes |
|---------|---------------|-------------|
| `controller/` | REST API endpoints (28 controllers, 206 endpoints) | SnapshotController, ServerController, AuthController |
| `service/` | Business logic (35+ services) | SnapshotService, ServerService, AlertService |
| `entity/` | JPA entities (32 entities) | Snapshot, Server, Alert, User, StorageTarget |
| `repository/` | Spring Data JPA repositories | SnapshotRepository, ServerRepository |
| `dto/` | Request/Response DTOs (98 classes, @Schema annotated) | CreateSnapshotRequest, SnapshotDTO, ServerDTO |
| `ssh/` | SSH connection pool (Apache MINA SSHD) | SshConnectionManager, SshConnection |
| `snapshot/` | Backup engine (Restic CLI wrapper) | SnapshotEngine, ResticClient |
| `diff/` | State diff engine (JSON comparison) | StateDiffEngine |
| `storage/` | Multi-storage backend | StorageRouter, S3StorageProvider, OssStorageProvider |
| `task/` | Async task execution | AsyncTaskManager, TaskType |
| `ai/` | AI analysis (MiMo/OpenAI) | AiClient, AiAnalysisService |
| `security/` | JWT auth, AES-256-GCM encryption | JwtTokenProvider, CredentialEncryptor |
| `websocket/` | STOMP WebSocket handler | EventWebSocketHandler |
| `config/` | Spring configuration | SecurityConfig, CorsConfig |
| `health/` | Health indicators | AgentHealthIndicator |
| `metrics/` | Micrometer custom metrics | SnapshotMetrics, SshMetrics |
| `audit/` | Audit logging | AuditLogAspect |

### Frontend Structure (`src/`)

| Directory | Purpose | Count |
|-----------|---------|-------|
| `views/` | Page components | 16 views |
| `api/` | Axios API modules | 14 modules |
| `components/` | Reusable UI components | DiffViewer, StateTree, SkeletonLoader, etc. |
| `components/modals/` | Modal dialogs | 11 modals |
| `stores/` | Pinia state stores | auth, modal, toast, app, layout |
| `composables/` | Vue composables | useWebSocket, useLoading |
| `router/` | Vue Router config | 16 routes |
| `types/` | TypeScript type definitions | API types, STOMP declarations |

### Agent Structure (`agent/`)

| Package | Purpose |
|---------|---------|
| `cmd/` | CLI commands (scan, run, health) |
| `scanner/` | State collection (packages, services, ports, docker, configs, crontab) |
| `restic/` | Restic CLI wrapper (init, backup, restore, check, forget) |
| `transport/` | HTTP/WebSocket communication with Backend |
| `config/` | Agent configuration (YAML) |

---

## Data Flow

### 1. Snapshot Creation Flow

```
User clicks "Create Snapshot" in UI
    │
    ▼
Frontend: POST /api/snapshots
    │
    ▼
Backend: SnapshotController.createSnapshot()
    │
    ├── Validate request (serverId, type, title, note)
    ├── Create AsyncTask (status: QUEUED)
    ├── Return 201 with task ID
    │
    ▼ (async)
SnapshotEngine.executeSnapshot()
    │
    ├── 1. SSH connect to server (SshConnectionManager)
    ├── 2. Pre-flight check (disk space: df -h /)
    ├── 3. Ensure Restic installed (auto-install if missing)
    ├── 4. Initialize Restic repo (idempotent: check if exists)
    ├── 5. Pre-hooks (MySQL lock, Redis BGSAVE)
    ├── 6. Execute: restic backup --tag chronovault
    ├── 7. Parse manifest (files_new, bytes_added, etc.)
    ├── 8. Collect state.json via Agent
    │   ├── Agent: scanner.PackagesCollector.Collect()
    │   ├── Agent: scanner.ServicesCollector.Collect()
    │   ├── Agent: scanner.PortsCollector.Collect()
    │   ├── Agent: scanner.DockerCollector.Collect()
    │   ├── Agent: scanner.ConfigsCollector.Collect()
    │   └── Agent: scanner.CrontabCollector.Collect()
    ├── 9. Post-hooks (MySQL unlock, Redis BGSAVE)
    ├── 10. Verify: restic check
    ├── 11. Save Snapshot to DB (state_json, change_summary)
    ├── 12. Update AsyncTask (status: COMPLETED)
    └── 13. WebSocket push: /topic/tasks/{id}
```

### 2. Snapshot Diff Flow

```
User selects two snapshots to compare
    │
    ▼
Frontend: GET /api/snapshots/{id1}/diff/{id2}
    │
    ▼
Backend: SnapshotController.getSnapshotDiff()
    │
    ├── Load both snapshots from DB
    ├── Parse state_json (JSON)
    ├── StateDiffEngine.diff(stateA, stateB)
    │   ├── diffPackages() → added, removed, upgraded
    │   ├── diffServices() → status changes
    │   ├── diffPorts() → new/closed ports
    │   ├── diffDocker() → container changes
    │   ├── diffConfigs() → file hash changes + unified diff
    │   ├── diffCrontab() → cron entry changes
    │   └── diffOs() → OS/kernel version changes
    ├── Generate change_summary_json
    └── Return SnapshotDiffDTO
```

### 3. Rollback Flow

```
User selects snapshot + rollback items
    │
    ▼
Frontend: POST /api/snapshots/{id}/rollback
    │
    ▼
Backend: SnapshotController.rollback()
    │
    ├── 1. Load snapshot + server info
    ├── 2. SnapshotService.rollbackInTransaction()
    │   └── Update snapshot status to ROLLING_BACK
    ├── 3. SnapshotService.rollbackViaSsh() (outside transaction)
    │   ├── SSH connect to server
    │   ├── Execute: restic restore --target /
    │   └── For selective rollback:
    │       ├── Restore specific files via SSH
    │       └── Revert packages via SSH (apt/yum)
    ├── 4. Update snapshot status to ROLLED_BACK
    └── 5. Create audit log entry
```

---

## Database Schema

### Core Tables (16)

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `users` | User accounts | id, name, email, password_hash, role, created_at |
| `servers` | Managed servers | id, name, ip, os, status, ssh_port, ssh_key_encrypted, auto_snapshot_enabled |
| `snapshots` | Backup snapshots | id, server_id, title, status, state_json, change_summary_json, size_bytes |
| `snapshot_diffs` | Per-field diffs | id, snapshot_a_id, snapshot_b_id, file_path, prev_value, next_value |
| `snapshot_tags` | Snapshot labels | id, snapshot_id, name, color, created_at |
| `snapshot_hooks` | Pre/post hooks | id, server_id, hook_type, command, enabled |
| `snapshot_manifests` | Restic metadata | id, snapshot_id, files_new, files_changed, bytes_added |
| `snapshot_retention_policies` | Cleanup rules | id, server_id, keep_daily, keep_weekly, keep_monthly |
| `storage_targets` | Backup destinations | id, type, name, endpoint, credentials_encrypted, total_bytes |
| `alerts` | Generated alerts | id, severity, title, description, category, status |
| `alert_rules` | Alert thresholds | id, name, metric, threshold, enabled |
| `async_tasks` | Background tasks | id, type, status, progress, result, server_id |
| `events` | System events | id, type, server_id, snapshot_id, message |
| `audit_logs` | User actions | id, user_id, action, resource_type, resource_id, ip_address |
| `api_keys` | API authentication | id, name, key_hash, prefix, scope |
| `agent_info` | Agent registry | id, server_id, agent_version, capabilities, last_heartbeat |

### Extended Tables (16)

| Table | Purpose |
|-------|---------|
| `server_groups` | Server grouping (prod/staging/dev) |
| `server_branches` | Git-style branches per server |
| `scheduled_backups` | Cron-based backup schedules |
| `container_states` | Docker container snapshots |
| `containers` | Monitored container registry |
| `volumes` | Docker volume tracking |
| `ai_insights` | AI analysis results |
| `ai_recommendations` | AI optimization suggestions |
| `risks` | Detected security risks |
| `integrations` | External service integrations |
| `webhook_endpoints` | Webhook notification targets |
| `webhook_delivery_logs` | Webhook delivery history |
| `team_members` | Team membership |
| `disaster_recovery_plans` | DR plan definitions |
| `verification_jobs` | Backup verification tasks |
| `system_settings` | App configuration KV store |

---

## API Design

### Base URL

```
/api/v1/  (versioned, future-proof)
```

### Authentication

- **JWT Bearer Token**: Required for all endpoints except `/api/auth/login`, `/api/auth/register`, `/actuator/health`
- **API Key**: Used for Agent → Backend communication (`X-API-Key` header)
- **HMAC Signature**: Agent requests signed with `X-Signature` header

### Request/Response Format

```json
// Success
{
  "code": 0,
  "message": "success",
  "data": { ... }
}

// Error
{
  "code": 40001,
  "message": "快照标题不能为空",
  "data": null
}

// Paginated
{
  "code": 0,
  "message": "success",
  "data": {
    "content": [...],
    "totalElements": 150,
    "totalPages": 8,
    "size": 20,
    "number": 0
  }
}
```

### Key Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | User login (returns JWT) |
| POST | `/api/auth/register` | User registration |
| GET | `/api/servers` | List servers (paged) |
| POST | `/api/servers` | Create server |
| POST | `/api/servers/{id}/connect` | Test SSH connection |
| GET | `/api/snapshots` | List snapshots (paged) |
| POST | `/api/snapshots` | Create snapshot |
| GET | `/api/snapshots/{id}/diff/{id2}` | Compare two snapshots |
| POST | `/api/snapshots/{id}/rollback` | Rollback to snapshot |
| GET | `/api/dashboard/overview` | Dashboard summary |
| GET | `/api/alerts` | List alerts (paged) |
| POST | `/api/agent/heartbeat` | Agent heartbeat |

---

## Security Architecture

### Authentication Flow

```
1. User submits email + password
2. Backend validates credentials (BCrypt)
3. Backend generates JWT (HS256, 24h expiry)
4. Backend generates refresh token (7d expiry)
5. Frontend stores in localStorage
6. Frontend attaches Authorization: Bearer <token> to all requests
7. Backend validates on each request via JwtTokenProvider
```

### Credential Encryption

```
SSH Key/Password
    │
    ▼
AES-256-GCM Encryption
    │ Key: CHRONOVAULT_MASTER_KEY (32 bytes)
    │ IV:   Random per encryption
    │ Auth: Additional data (server ID)
    │
    ▼
Encrypted Blob (stored in DB)
    │
    ▼
Decryption on-demand (SshConnectionManager)
```

### Security Layers

| Layer | Mechanism |
|-------|-----------|
| Transport | HTTPS (TLS 1.3 in production) |
| Authentication | JWT (HS256) + API Keys |
| Authorization | Role-based (OWNER > ADMIN > MEMBER > VIEWER) |
| Input Validation | Jakarta Validation (@NotBlank, @Size, @Pattern) |
| XSS Prevention | SanitizeUtil (HTML entity escaping) |
| Rate Limiting | Redis-based (IP + user dimension) |
| Credential Storage | AES-256-GCM encrypted |
| SSH Verification | TOFU (default) / known_hosts (production) |

---

## Deployment Topology

### Development (Local)

```
┌─────────────────────────────────────────┐
│            Developer Machine             │
│                                         │
│  ┌──────────┐  ┌────────────────────┐  │
│  │ Frontend │  │ Backend            │  │
│  │ :5173    │→ │ :8080              │  │
│  │ (Vite)   │  │ (Spring Boot dev)  │  │
│  └──────────┘  └────────┬───────────┘  │
│                         │               │
│  ┌──────────────────────┴────────────┐ │
│  │         Docker Compose            │ │
│  │  ┌──────────┐  ┌──────────┐      │ │
│  │  │PostgreSQL│  │ Redis    │      │ │
│  │  │ :5432    │  │ :6379    │      │ │
│  │  └──────────┘  └──────────┘      │ │
│  └───────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### Production (Docker Compose)

```
┌─────────────────────────────────────────────────────────────┐
│                    Production Server                         │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Docker Compose Network                  │   │
│  │                                                      │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │   │
│  │  │ Frontend │  │ Backend  │  │ PostgreSQL       │  │   │
│  │  │ (nginx)  │→ │ :8080    │→ │ :5432            │  │   │
│  │  │ :80      │  │          │  │ (internal only)  │  │   │
│  │  └──────────┘  └──────────┘  └──────────────────┘  │   │
│  │                    │         ┌──────────────────┐   │   │
│  │                    │         │ Redis            │   │   │
│  │                    └────────→│ :6379            │   │   │
│  │                              │ (internal only)  │   │   │
│  │                              └──────────────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Target Servers (SSH)                    │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐         │   │
│  │  │Agent :8081│  │Agent     │  │Agent     │         │   │
│  │  │(Web App) │  │(DB Svr)  │  │(Cache)   │         │   │
│  │  └──────────┘  └──────────┘  └──────────┘         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Resource Requirements

| Component | CPU | Memory | Disk |
|-----------|-----|--------|------|
| Frontend (nginx) | 0.1 core | 64 MB | 50 MB |
| Backend (JVM) | 1-2 cores | 512 MB-1 GB | 1 GB |
| PostgreSQL | 0.5-1 core | 256 MB-512 MB | 10 GB+ |
| Redis | 0.1 core | 128 MB | 1 GB |
| Agent (Go) | 0.1 core | 64 MB | 50 MB |

---

## Development Guide

### Code Style

**Backend (Java 17)**:
- Lombok: `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`
- Logging: `@Slf4j` with structured format `[ACTION] [target=id] description`
- DTOs: Java records with `@Schema` annotations for Swagger
- Validation: Jakarta Validation (`@NotBlank`, `@Size`, `@Pattern`)

**Frontend (Vue 3 + TypeScript)**:
- Composition API with `<script setup lang="ts">`
- Tailwind CSS 4 with Material Design 3 tokens
- Icons: `material-symbols-outlined`
- Components: glass-panel class for cards

**Agent (Go 1.22)**:
- Standard Go project layout
- Error wrapping with `fmt.Errorf("context: %w", err)`
- Structured logging with `log/slog`

### Testing Strategy

| Layer | Tool | Coverage |
|-------|------|----------|
| Unit tests | JUnit 5 + Mockito | 697 tests (backend) |
| Integration tests | Testcontainers | PostgreSQL + Redis |
| Frontend unit | Vitest + Vue Test Utils | Core API/store logic |
| E2E tests | Playwright | Login → Snapshot → Diff flow |
| Agent tests | Go testing | Scanner, Restic client |

### Database Migrations

```bash
# Check current version
ls backend/src/main/resources/db/migration/ | sort -V | tail -1

# Create new migration (increment version)
# V43__description.sql
```

Rules:
- Never modify existing migrations
- Use `IF NOT EXISTS` for additive changes
- Test rollback manually (Flyway Community doesn't support undo)
- Demo data in V12 (skipped in prod via profile)

### Key Design Decisions

1. **Restic CLI over Go library**: Simpler integration, auto-install capability, battle-tested
2. **SSH connection pooling**: Reuse connections across operations, idle eviction
3. **Async task tracking**: DB-backed progress with WebSocket push
4. **state.json as core differentiator**: Enables diff, blame, bisect — not just file backup
5. **Git-style UX**: Branches, stashes, cherry-pick — familiar to developers
