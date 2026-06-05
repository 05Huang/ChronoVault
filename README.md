# ChronoVault

[![CI](https://github.com/chronovault/chronovault/actions/workflows/ci.yml/badge.svg)](https://github.com/chronovault/chronovault/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/badge/tests-697-passing-brightgreen)](#testing)
[![Coverage](https://img.shields.io/badge/coverage-85%25-brightgreen)](#testing)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.1.0--alpha-orange)](CHANGELOG.md)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.5-brightgreen)](https://vuejs.org/)
[![Go](https://img.shields.io/badge/Go-1.22-00ADD8?logo=go&logoColor=white)](https://go.dev/)

**Git for Server State** — The world's first tool that manages server state like git manages code.

## Overview

ChronoVault is a server backup and recovery platform that provides Git-like operations for server state management. It enables administrators to:

- **Snapshot** server state with full/incremental backups
- **Branch** server environments (production, staging, development)
- **Diff** changes between snapshots
- **Revert** specific snapshot changes
- **Bisect** to find which snapshot introduced a problem
- **Clone** servers with full state replication

## Why ChronoVault?

Traditional backup tools only capture files. ChronoVault captures **everything** — packages, services, ports, Docker containers, configs, and crontab. This enables:

- **State-aware snapshots**: Not just files, but the entire system state
- **Intelligent diffs**: See exactly what changed between snapshots (packages upgraded, services restarted, ports opened)
- **Selective rollback**: Roll back specific packages or config files without restoring everything
- **Change detection**: Automatic alerts when high-risk changes occur (new ports, service disabling, critical config edits)

## Comparison with Backrest

| Feature | ChronoVault | Backrest |
|---------|-------------|----------|
| **State Collection** | ✅ Packages, services, ports, Docker, configs, crontab | ❌ Files only |
| **State Diff** | ✅ See exactly what changed (packages, services, ports) | ❌ File-level diff only |
| **Selective Rollback** | ✅ Roll back individual packages or config files | ❌ Full restore only |
| **Git-like Operations** | ✅ Branch, Bisect, Cherry-pick, Stash, Blame | ❌ Basic backup/restore |
| **Multi-server** | ✅ Centralized management | ⚠️ Single server |
| **Change Alerts** | ✅ Auto-detect high-risk changes | ❌ No alerting |
| **Timeline View** | ✅ Git-log style with change summaries | ❌ Basic list view |
| **Diff Visualization** | ✅ Color-coded state changes with rollback buttons | ❌ No visual diff |

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

### Agent Installation

```bash
# Install agent on target server
curl -sSL https://raw.githubusercontent.com/your-org/chronovault/main/install-agent.sh | bash

# Or manually
wget https://github.com/your-org/chronovault/releases/latest/download/chronovault-agent-linux-amd64
chmod +x chronovault-agent-linux-amd64
./chronovault-agent-linux-amd64 register --server-url http://your-backend:8080 --api-key YOUR_API_KEY
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

### State-Aware Features (Core Differentiator)
- **state.json Collection**: Agent collects comprehensive system state during each snapshot
  - Installed packages (apt/dpkg, rpm/yum, apk)
  - Systemd service status and enabled state
  - Open ports with process association
  - Docker containers and compose files
  - /etc config file SHA-256 hashes
  - Crontab entries
- **State Diff Engine**: Compare state.json between snapshots to show:
  - Package upgrades, additions, and removals
  - Service status changes
  - Port changes
  - Config file modifications
  - Docker container changes
- **Selective Rollback**: Roll back specific items from the diff view:
  - Config files: Extract from Restic snapshot and write via SSH
  - Packages: Execute apt/yum install with specific version
  - Services: Re-enable disabled services
- **Change Detection**: Automatic alerts for high-risk changes:
  - New high-risk ports (22, 3306, 5432, 6379)
  - Service disabling
  - Critical config changes (/etc/hosts, /etc/sudoers, /etc/passwd)

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
VITE_WS_URL=ws://localhost:8080/ws/events

# Agent (on target server)
CHRONOVAULT_SERVER_URL=http://your-backend:8080
CHRONOVAULT_API_KEY=your-api-key
```

### Docker Compose (Full Stack)
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

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - CHRONOVAULT_DB_URL=jdbc:postgresql://postgres:5432/chronovault
      - CHRONOVAULT_REDIS_HOST=redis
      - CHRONOVAULT_JWT_SECRET=${JWT_SECRET}
      - CHRONOVAULT_RESTIC_PASSWORD=${RESTIC_PASSWORD}
    depends_on:
      - postgres
      - redis

  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - backend
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
│   │   ├── diff/            # State diff engine (state.json comparison)
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
│       ├── db/migration/    # Flyway migrations (V1-V39)
│       └── logback-spring.xml
├── frontend/                # Vue 3 SPA
│   └── src/
│       ├── api/             # API client modules
│       ├── components/      # Vue components (StateTree, TaskProgress, etc.)
│       ├── composables/     # Vue composables (useWebSocket, etc.)
│       ├── stores/          # Pinia state stores
│       ├── types/           # TypeScript interfaces
│       └── views/           # Page views (Timeline, SnapshotDiff, etc.)
├── agent/                   # Go CLI daemon
│   ├── cmd/                 # CLI commands
│   ├── config/              # Configuration
│   ├── restic/              # Restic CLI wrapper
│   ├── scanner/             # Environment scanner (state.json collection)
│   ├── server/              # Local API server
│   └── transport/           # HTTP client
└── docker-compose.yml
```

## License

MIT License