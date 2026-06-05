# ChronoVault v0.1.0

> Initial release of ChronoVault - Git for Server State

## What is ChronoVault?

ChronoVault is a server state version control platform that enables teams to manage server environments like code. It provides Git-like operations for server state management, including snapshots, diffs, branches, and rollback.

### Key Differentiators

- **State-Aware Snapshots**: Not just files - captures packages, services, ports, Docker containers, configs, and crontab
- **Git-like Operations**: Branch, stash, bisect, cherry-pick, blame - familiar workflow for developers
- **Multi-Server Management**: Centralized control of multiple servers from a single dashboard
- **Intelligent Diff**: See exactly what changed between snapshots (packages upgraded, services restarted, ports opened)

---

## Features

### Core Features

- **Snapshot Creation**: Full/incremental backups with state.json collection
- **State Diff**: Compare packages, services, ports, configs between snapshots
- **Selective Rollback**: Roll back specific changes without full restore
- **Timeline View**: Git-log style history with change summaries
- **Branching**: Create parallel state tracks for experimentation
- **Stash**: Temporarily shelve changes for quick recovery
- **Bisect**: Find which snapshot introduced a problem
- **Cherry-pick**: Apply specific changes to another server

### Infrastructure Features

- **Multi-Server Support**: Manage multiple servers from one dashboard
- **Scheduled Backups**: Cron-based automated snapshots
- **Retention Policies**: Automatic cleanup of old snapshots
- **Storage Replication**: Copy snapshots across storage targets
- **Webhook Notifications**: Slack, DingTalk, custom URL integrations
- **Disaster Recovery**: Runbook-based recovery plans
- **Verification Jobs**: Periodic backup integrity checks

### Security Features

- **AES-256-GCM Encryption**: Credentials encrypted at rest
- **JWT Authentication**: Secure token-based auth
- **Role-Based Access Control**: OWNER, ADMIN, MEMBER, VIEWER roles
- **Audit Logging**: All actions tracked for accountability
- **SSH Key Rotation**: Periodic key rotation support

### AI Features

- **Smart Insights**: AI-powered analysis of server state
- **Risk Detection**: Automatic identification of risky changes
- **Storage Prediction**: Forecast storage usage trends
- **Recommendations**: AI suggestions for optimization

---

## Installation

### Quick Start (Docker)

```bash
git clone https://github.com/chronovault/chronovault.git
cd chronovault

# Generate security keys
cat > .env << EOF
POSTGRES_DB=chronovault
POSTGRES_USER=chronovault
POSTGRES_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -hex 32)
CHRONOVAULT_MASTER_KEY=$(openssl rand -hex 32)
CHRONOVAULT_RESTIC_PASSWORD=$(openssl rand -hex 32)
SPRING_PROFILES_ACTIVE=prod
EOF

# Start services
docker compose up -d --build
```

Open http://localhost and register your first account.

### Agent Installation

Install the Agent on each server you want to manage:

```bash
# Linux amd64
curl -fsSL https://github.com/chronovault/chronovault/releases/download/v0.1.0/chronovault-agent-linux-amd64 -o /usr/local/bin/chronovault-agent
chmod +x /usr/local/bin/chronovault-agent

# Configure
mkdir -p /etc/chronovault
cat > /etc/chronovault/agent.yaml << EOF
server:
  backend_url: "http://your-chronovault-server:8080"
  api_key: "your-api-key"
  server_name: "web-server-01"
EOF

# Run
chronovault-agent run
```

---

## System Requirements

### Backend

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 2 cores | 4 cores |
| Memory | 4 GB | 8 GB |
| Disk | 50 GB | 100 GB+ |
| OS | Ubuntu 20.04+ | Ubuntu 22.04 LTS |

### Agent

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 1 core | 2 cores |
| Memory | 256 MB | 512 MB |
| Disk | 1 GB | 10 GB |
| OS | Linux (amd64/arm64) | Ubuntu 20.04+ |

---

## Agent Binaries

| Platform | Architecture | Binary |
|----------|--------------|--------|
| Linux | amd64 | `chronovault-agent-linux-amd64` |
| Linux | arm64 | `chronovault-agent-linux-arm64` |
| macOS | amd64 | `chronovault-agent-darwin-amd64` |
| macOS | arm64 (Apple Silicon) | `chronovault-agent-darwin-arm64` |
| Windows | amd64 | `chronovault-agent-windows-amd64.exe` |

---

## What's Changed Since Alpha

### New Features

- State-aware snapshots with comprehensive system state collection
- Git-like operations (branch, stash, bisect, cherry-pick, blame)
- Timeline view with change summaries
- Selective rollback capability
- Webhook notifications (Slack, DingTalk)
- Disaster recovery plans
- AI-powered insights and recommendations
- Multi-server management with server groups
- Scheduled backups with cron expressions
- Retention policies for automatic cleanup
- Storage replication across targets
- Verification jobs for backup integrity
- Terminal access for remote command execution

### Security Improvements

- AES-256-GCM encryption for all credentials
- JWT authentication with role-based access control
- SSH key rotation support
- Comprehensive audit logging
- Rate limiting and brute force protection
- Input validation and XSS prevention

### Performance Improvements

- Optimized database queries (N+1 elimination)
- Redis caching for frequently accessed data
- Connection pooling for SSH and database
- Async task execution with progress tracking
- WebSocket real-time updates

### Infrastructure

- Docker Compose deployment
- GitHub Actions CI/CD pipeline
- Prometheus metrics and Grafana dashboards
- Multi-stage Docker builds
- Health checks and resource limits

---

## Known Issues

- SSH Trust-On-First-Use (TOFU) mode - production should configure known_hosts
- AI features require external API (MiMo or OpenAI compatible)
- Windows Agent is experimental

---

## Documentation

- [Quick Start Guide](docs/QUICKSTART.md)
- [User Guide](docs/USER_GUIDE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [API Reference](docs/API_REFERENCE.md)
- [Deployment Guide](docs/DEPLOYMENT.md)
- [Agent Installation](docs/AGENT_INSTALLATION.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Security](docs/SECURITY.md)
- [Contributing](docs/CONTRIBUTING.md)

---

## Support

- **Documentation**: https://github.com/chronovault/chronovault/tree/main/docs
- **Issues**: https://github.com/chronovault/chronovault/issues
- **Discussions**: https://github.com/chronovault/chronovault/discussions

---

## License

MIT License - see [LICENSE](LICENSE) for details.

---

## Acknowledgments

- [Backrest](https://github.com/garethgeorge/backrest) - Inspiration for backup architecture
- [Restic](https://restic.net/) - Excellent backup tool
- [Spring Boot](https://spring.io/projects/spring-boot) - Backend framework
- [Vue.js](https://vuejs.org/) - Frontend framework
- [Go](https://go.dev/) - Agent language
