# ChronoVault

**Git for Server State** — The world's first tool that manages server state like git manages code.

## Overview

ChronoVault is a server backup and recovery platform that provides Git-like operations for server state management. It enables administrators to:

- **Snapshot** server state with full/incremental backups
- **Branch** server environments (production, staging, development)
- **Diff** changes between snapshots
- **Revert** specific snapshot changes
- **Bisect** to find which snapshot introduced a problem
- **Clone** servers with full state replication

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Frontend  │────▶│   Backend   │────▶│   Agent     │
│   (Vue 3)   │     │  (Spring    │     │   (Go)      │
│             │     │    Boot)    │     │             │
└─────────────┘     └─────────────┘     └─────────────┘
       │                  │                    │
       │                  ▼                    ▼
       │            ┌───────────┐        ┌───────────┐
       │            │ PostgreSQL │        │  Restic   │
       │            │   Redis    │        │   CLI     │
       │            └───────────┘        └───────────┘
       │
       ▼
  ┌─────────┐
  │  Vite   │
  └─────────┘
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Frontend | Vue 3.5, TypeScript 6, Vite 8, Tailwind CSS 4, Pinia |
| Backend | Java 17, Spring Boot 3.2.5, PostgreSQL 15, Redis 7 |
| Agent | Go 1.22, Restic CLI |
| Database Migrations | Flyway |
| Backup Engine | Restic |

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Node.js 18+
- Go 1.22+ (for agent)

### Development Setup

```bash
# 1. Start infrastructure
docker-compose up -d postgres redis

# 2. Start backend
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Start frontend
cd frontend
npm install
npm run dev
```

### Default Credentials
- Email: `admin@chronovault.com`
- Password: `admin123`

## Features

### Core Features (Git for Server State)
- **Snapshot (git commit)**: Create full/incremental backups with selective paths
- **Rollback (git checkout)**: Restore server to any previous snapshot
- **Revert (git revert)**: Undo specific snapshot changes
- **Branch (git branch)**: Parallel state tracks per server
- **Bisect (git bisect)**: Find which snapshot introduced a problem
- **Clone (git clone)**: Replicate server configuration
- **Cherry-pick (git cherry-pick)**: Apply specific changes to target server
- **Stash (git stash)**: Quick temporary saves
- **Blame (git blame)**: Who changed what and when

### Infrastructure Features
- **Drift Detection**: Monitor container, file, and port changes
- **Auto-snapshot**: Automatic snapshots on drift detection
- **Scheduled Backups**: Cron-based automated backups
- **Webhooks**: Event-driven integrations (Slack, DingTalk, custom)
- **Verification Jobs**: Periodic backup integrity checks
- **Disaster Recovery**: Runbook-based recovery plans

### Operations Features
- **Server Groups**: Organize servers by environment (prod/staging/dev)
- **Multi-server Snapshots**: Batch snapshot across multiple servers
- **Storage Replication**: Cross-target backup replication
- **Pre/Post Hooks**: User-configurable automation hooks
- **Container State Capture**: Docker container state in snapshots

## API Reference

### Snapshot APIs
```
POST   /api/snapshots              Create snapshot
GET    /api/snapshots              List snapshots
GET    /api/snapshots/{id}         Get snapshot details
DELETE /api/snapshots/{id}         Delete snapshot
POST   /api/snapshots/{id}/rollback   Rollback to snapshot
POST   /api/snapshots/{id}/revert     Revert snapshot changes
POST   /api/snapshots/{id}/verify     Verify snapshot integrity
POST   /api/snapshots/{id}/cherry-pick  Apply changes to target
POST   /api/snapshots/{id}/restore-files  Selective file restore
GET    /api/snapshots/{id}/files       Browse snapshot files
GET    /api/snapshots/{id}/diff        Get snapshot diff
POST   /api/snapshots/bisect/start     Start bisect session
GET    /api/snapshots/compare          Compare two snapshots
```

### Server APIs
```
GET    /api/servers              List servers
POST   /api/servers              Add server
GET    /api/servers/{id}         Get server details
PUT    /api/servers/{id}/auto-snapshot  Toggle auto-snapshot
POST   /api/servers/clone        Clone server
GET    /api/servers/{id}/drift   Run drift detection
```

### Branch APIs
```
GET    /api/servers/{id}/branches      List branches
POST   /api/servers/{id}/branches      Create branch
DELETE /api/servers/{id}/branches/{id} Delete branch
POST   /api/servers/{id}/branches/{id}/switch  Switch branch
POST   /api/servers/{id}/branches/merge  Merge branches
```

### Stash APIs
```
POST   /api/servers/{id}/stash         Create stash
GET    /api/servers/{id}/stash         List stashes
POST   /api/servers/{id}/stash/pop     Pop latest stash
DELETE /api/servers/{id}/stash/{id}    Discard stash
```

### Settings APIs
```
GET    /api/scheduled-backups          List scheduled backups
POST   /api/scheduled-backups          Create scheduled backup
GET    /api/retention-policies         List retention policies
POST   /api/webhooks                   Manage webhooks
GET    /api/verification-jobs          List verification jobs
GET    /api/disaster-recovery          List DR plans
```

## Configuration

### Environment Variables
```bash
# Backend
CHRONOVAULT_DB_URL=jdbc:postgresql://localhost:5432/chronovault
CHRONOVAULT_DB_USERNAME=postgres
CHRONOVAULT_DB_PASSWORD=password
CHRONOVAULT_REDIS_HOST=localhost
CHRONOVAULT_REDIS_PORT=6379
CHRONOVAULT_JWT_SECRET=your-secret-key-min-32-chars
CHRONOVAULT_RESTIC_PASSWORD=your-restic-password

# Frontend
VITE_API_URL=http://localhost:8080
```

### Docker Compose
```yaml
services:
  postgres:
    image: postgres:15
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: chronovault
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

## Project Structure

```
chronovault/
├── backend/                 # Spring Boot REST API
│   ├── src/main/java/com/chronovault/
│   │   ├── audit/           # Audit logging (AOP)
│   │   ├── config/          # Security, CORS, WebSocket configs
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── entity/          # JPA entities
│   │   ├── health/          # Health indicators
│   │   ├── metrics/         # Micrometer metrics
│   │   ├── repository/      # JPA repositories
│   │   ├── service/         # Business logic
│   │   ├── snapshot/        # ResticClient, SnapshotEngine
│   │   ├── ssh/             # SSH connection management
│   │   ├── storage/         # Storage providers
│   │   ├── task/            # Async task management
│   │   └── websocket/       # WebSocket handlers
│   └── src/main/resources/
│       ├── db/migration/    # Flyway migrations (V1-V36)
│       └── logback-spring.xml
├── frontend/                # Vue 3 SPA
│   └── src/
│       ├── api/             # API client modules
│       ├── components/      # Vue components
│       ├── stores/          # Pinia state stores
│       ├── types/           # TypeScript interfaces
│       └── views/           # Page views
├── agent/                   # Go CLI daemon
│   ├── cmd/                 # CLI commands
│   ├── config/              # Configuration
│   ├── restic/              # Restic CLI wrapper
│   ├── scanner/             # Environment scanner
│   ├── server/              # Local API server
│   └── transport/           # HTTP client
└── docker-compose.yml
```

## License

MIT License