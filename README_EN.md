<div align="center">

<img src="logo.webp" alt="ChronoVault Logo" width="400">

# ChronoVault

**Git for Server State**

[![CI](https://img.shields.io/badge/CI-passing-brightgreen)](#testing)
[![Tests](https://img.shields.io/badge/tests-697%20passing-brightgreen)](#testing)
[![Coverage](https://img.shields.io/badge/coverage-85%25-brightgreen)](#testing)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.1.0--alpha-orange)](CHANGELOG.md)

[English](README_EN.md) | [中文文档](README.md) | [Quick Start](#-quick-start) | [Community](#-community--support)

</div>

---

## What is ChronoVault?

ChronoVault is the world's first platform that applies Git concepts to server state management. While traditional backup tools only capture files, ChronoVault captures **the entire system state** -- including installed packages, running services, listening ports, Docker containers, config file hashes, and crontab entries.

**Just like Git manages code, ChronoVault manages server state.**

## Why ChronoVault?

**Pain Points of Traditional Backup Tools:**

- Can only backup files, no awareness of system state changes
- Full restore only -- no selective rollback capability
- Cannot track who changed what and when
- Lack of Git-style branching and version comparison
- Complex multi-server management without unified view

**ChronoVault Solution:**

- **State-aware snapshots**: Capture complete system state, not just files
- **Intelligent diffs**: Precisely show package upgrades, service restarts, port changes
- **Selective rollback**: Roll back individual configs or packages without full restore
- **Git-like workflow**: Branch, Diff, Bisect, Cherry-pick -- familiar to developers
- **Centralized management**: One dashboard for all your servers

## Feature Comparison

| Capability | ChronoVault | Traditional Backup (Restic) | Config Management (Ansible) |
|-----------|-------------|---------------------------|---------------------------|
| **State Collection** | Packages, services, ports, Docker, configs, crontab | Files only | Manual definition required |
| **State Diff** | Visual diffs (packages/services/ports/configs) | File-level diff only | Not supported |
| **Selective Rollback** | Per-package or per-config file level | Full restore only | Manual operation required |
| **Git-like Operations** | Branch, Bisect, Cherry-pick, Stash, Blame | Not supported | Not supported |
| **Change Tracking** | Who changed what, and when | Not supported | Playbook logs only |
| **Multi-server** | Centralized dashboard | Single server | Supported |
| **Alerts** | Auto-detect high-risk changes | Not supported | Not supported |

## Features

### Core Features

| Operation | Git Equivalent | Description |
|-----------|---------------|-------------|
| **Snapshot** | `git commit` | Create full/incremental snapshots capturing complete system state |
| **Rollback** | `git checkout` | Restore server to any historical snapshot |
| **Revert** | `git revert` | Undo changes from a specific snapshot |
| **Branch** | `git branch` | Create parallel state tracks per server (prod/staging/dev) |
| **Bisect** | `git bisect` | Binary search to find which snapshot introduced a problem |
| **Clone** | `git clone` | Replicate server configuration to a new environment |
| **Cherry-pick** | `git cherry-pick` | Apply specific changes to a target server |
| **Stash** | `git stash` | Quick temporary state saves |
| **Blame** | `git blame` | View who changed what and when |

### Infrastructure Features

- **Drift Detection** -- Monitor container, file, and port changes
- **Auto-snapshot** -- Automatic snapshots when drift is detected
- **Scheduled Backups** -- Cron-based automated backups
- **Webhooks** -- Slack, DingTalk, and custom URL integrations
- **Verification Jobs** -- Periodic backup integrity checks
- **Disaster Recovery** -- Runbook-based recovery plans

### Security Features

- **AES-256-GCM Encryption** -- Credentials encrypted at rest
- **JWT Auth + RBAC** -- OWNER / ADMIN / MEMBER / VIEWER roles
- **Audit Logging** -- All operations are traceable
- **Rate Limiting** -- Protection against brute force and abuse
- **XSS Prevention** -- Input filtering and escaping

---

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Git

### One-Click Launch

```bash
# Clone the repository
git clone https://github.com/chronovault/chronovault.git
cd chronovault

# Start all services
docker-compose up -d

# Access the application
open http://localhost
```

### Default Credentials

| Field | Value |
|-------|-------|
| Email | `admin@chronovault.com` |
| Password | `admin123` |

> **Security Note**: Please change the default password immediately after your first login.

### Local Development

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

---

## Architecture

```
Frontend (Vue 3.5 + TypeScript + Vite + Pinia)
    |
    | REST API + WebSocket
    v
Backend (Java 17 + Spring Boot 3.2.5 + JPA)
    |          |           |
    v          v           v
PostgreSQL   Redis      Restic CLI
    |
    | SSH
    v
Agent (Go 1.22 + Restic CLI)
    |
    v
Target Server
```

### Tech Stack

| Component | Technology |
|-----------|------------|
| **Frontend** | Vue 3.5, TypeScript 5.3, Vite 5, Tailwind CSS, Pinia |
| **Backend** | Java 17, Spring Boot 3.2.5, Spring Security, JPA, Flyway |
| **Database** | PostgreSQL 15, Redis 7 |
| **Agent** | Go 1.22, Restic CLI |
| **Infrastructure** | Docker, Docker Compose, Nginx |
| **Monitoring** | Prometheus, Grafana, Micrometer |

---

## Project Structure

```
chronovault/
+-- backend/                  # Spring Boot REST API
|   +-- src/main/java/com/chronovault/
|   |   +-- controller/       # REST controllers
|   |   +-- service/          # Business logic
|   |   +-- repository/       # Data access layer
|   |   +-- entity/           # JPA entities
|   |   +-- dto/              # Data Transfer Objects
|   |   +-- diff/             # State diff engine
|   |   +-- snapshot/         # Snapshot engine
|   |   +-- ssh/              # SSH connection management
|   |   +-- websocket/        # WebSocket handlers
|   +-- src/main/resources/db/migration/  # Flyway migrations (42 versions)
|
+-- frontend/                 # Vue 3 SPA
|   +-- src/
|       +-- views/            # Page views
|       +-- components/       # Vue components
|       +-- api/              # API clients
|       +-- stores/           # Pinia state management
|       +-- types/            # TypeScript type definitions
|
+-- agent/                    # Go CLI daemon
|   +-- cmd/                  # CLI commands
|   +-- scanner/              # Environment scanner
|   +-- restic/               # Restic CLI wrapper
|   +-- transport/            # HTTP client
|
+-- terraform/                # Terraform Provider examples
+-- monitoring/               # Prometheus + Grafana configs
+-- docs/                     # Documentation
```

---

## API Documentation

Access Swagger UI after starting the backend:

```
http://localhost:8080/swagger-ui.html
```

Full API documentation: [API_REFERENCE.md](docs/API_REFERENCE.md)

---

## Deployment

### Docker Compose (Recommended)

```bash
docker-compose -f docker-compose.prod.yml up -d
```

### Agent Installation

```bash
wget https://github.com/chronovault/chronovault/releases/latest/download/chronovault-agent-linux-amd64
chmod +x chronovault-agent-linux-amd64
./chronovault-agent-linux-amd64 register --server-url http://your-backend:8080 --api-key YOUR_API_KEY
```

Deployment details: [DEPLOYMENT.md](docs/DEPLOYMENT.md) | [AGENT_INSTALLATION.md](docs/AGENT_INSTALLATION.md)

---

## Documentation

| Document | Description |
|----------|-------------|
| [Quick Start](docs/QUICKSTART.md) | Get started in 5 minutes |
| [Architecture](docs/ARCHITECTURE.md) | System architecture deep dive |
| [API Reference](docs/API_REFERENCE.md) | Complete API documentation |
| [User Guide](docs/USER_GUIDE.md) | Feature usage guide |
| [Agent Installation](docs/AGENT_INSTALLATION.md) | Agent deployment guide |
| [Deployment](docs/DEPLOYMENT.md) | Production deployment guide |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Common issues and solutions |
| [Contributing](CONTRIBUTING.md) | How to contribute |
| [Security](SECURITY.md) | Vulnerability reporting |

---

## Testing

```bash
# Backend tests
cd backend && mvn test

# Frontend type check
cd frontend && npx vue-tsc --noEmit

# Agent tests
cd agent && go test -race ./...
```

---

## Contributing

We welcome all forms of contribution! Please read [CONTRIBUTING.md](CONTRIBUTING.md) to get started.

### Development Workflow

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Create a Pull Request

### Commit Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/):

| Prefix | Description |
|--------|-------------|
| `feat:` | New feature |
| `fix:` | Bug fix |
| `docs:` | Documentation update |
| `style:` | Code style (no logic change) |
| `refactor:` | Code refactoring |
| `test:` | Test related |
| `chore:` | Build/toolchain changes |
| `perf:` | Performance improvement |

---

## Community & Support

- **GitHub Issues** -- [Report bugs or request features](https://github.com/chronovault/chronovault/issues)
- **Discussions** -- [Join the conversation](https://github.com/chronovault/chronovault/discussions)
- **Security** -- [Report vulnerabilities](SECURITY.md)

---

## Roadmap

- [x] **v0.1.0** -- Core features release (snapshot, branch, diff, rollback, multi-server)
- [ ] **v0.2.0** -- Performance optimization, reliability improvements, frontend test coverage
- [ ] **v0.3.0** -- Enterprise features (multi-tenant, SSO, compliance audit)
- [ ] **v1.0.0** -- Stable release

Full roadmap: [ROADMAP.md](ROADMAP.md)

---

## License

This project is licensed under the [MIT License](LICENSE).

---

<div align="center">

**If ChronoVault helps you, please give us a ⭐ Star!**

</div>

