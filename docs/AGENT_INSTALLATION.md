# ChronoVault Agent Installation Guide

> Install and configure the ChronoVault Agent on target servers

---

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Installation Methods](#installation-methods)
  - [Quick Install (Recommended)](#quick-install-recommended)
  - [Manual Install](#manual-install)
  - [Docker Install](#docker-install)
- [Platform-Specific Instructions](#platform-specific-instructions)
  - [Ubuntu/Debian](#ubuntudebian)
  - [CentOS/RHEL](#centosrhel)
  - [Alpine Linux](#alpine-linux)
  - [Amazon Linux](#amazon-linux)
- [Configuration](#configuration)
- [Systemd Service](#systemd-service)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)
- [Upgrading](#upgrading)
- [Uninstalling](#uninstalling)

---

## Overview

The ChronoVault Agent runs on each target server and:

1. **Collects system state** during snapshots:
   - Installed packages (apt/yum/apk)
   - Systemd services status
   - Open ports and processes
   - Docker containers and images
   - /etc configuration file hashes
   - Crontab entries

2. **Executes backup operations**:
   - Initializes Restic repository
   - Performs file backups
   - Restores files on rollback

3. **Reports to Backend**:
   - Sends heartbeats (every 60 seconds)
   - Reports task progress
   - Provides health status

---

## Prerequisites

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| OS | Linux (kernel 3.10+) | Ubuntu 20.04+ / CentOS 7+ |
| Architecture | amd64, arm64 | amd64 |
| Disk Space | 100 MB | 1 GB |
| Memory | 64 MB | 128 MB |
| Network | Outbound HTTPS to Backend | Low latency to Backend |

### Required Access

- **Root or sudo access**: Agent needs to read system state
- **Network access**: Outbound HTTPS to ChronoVault Backend (port 8080)
- **SSH access**: Backend connects to Agent via SSH for backup operations

---

## Installation Methods

### Quick Install (Recommended)

One-line installation script:

```bash
curl -fsSL https://raw.githubusercontent.com/chronovault/chronovault/main/install-agent.sh | bash
```

This script will:
1. Detect your OS and architecture
2. Download the latest Agent binary
3. Install to `/opt/chronovault/`
4. Create systemd service
5. Start the Agent

### Manual Install

#### 1. Download the Agent

```bash
# Detect architecture
ARCH=$(uname -m)
case $ARCH in
    x86_64) ARCH="amd64" ;;
    aarch64) ARCH="arm64" ;;
    armv7l) ARCH="arm" ;;
esac

# Download latest version
VERSION="0.1.0"
wget https://github.com/chronovault/chronovault/releases/download/v${VERSION}/chronovault-agent-linux-${ARCH}
chmod +x chronovault-agent-linux-${ARCH}
```

#### 2. Install the Binary

```bash
sudo mkdir -p /opt/chronovault
sudo mv chronovault-agent-linux-${ARCH} /opt/chronovault/chronovault-agent
```

#### 3. Create Configuration

```bash
sudo mkdir -p /etc/chronovault

cat > /etc/chronovault/agent.yaml << 'EOF'
server:
  backend_url: "http://your-chronovault-server:8080"
  api_key: "your-api-key"
  server_name: "web-server-01"

heartbeat:
  interval: 60

scanner:
  enabled_collectors:
    - packages
    - services
    - ports
    - docker
    - configs
    - crontab
  config_paths:
    - /etc/nginx
    - /etc/ssh
    - /etc/docker
  timeout: 10

restic:
  auto_install: true
  repository: "/var/lib/chronovault/restic"
EOF
```

#### 4. Create Systemd Service

```bash
sudo tee /etc/systemd/system/chronovault-agent.service << 'EOF'
[Unit]
Description=ChronoVault Agent
After=network.target docker.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/chronovault
ExecStart=/opt/chronovault/chronovault-agent run
Restart=always
RestartSec=10
EnvironmentFile=-/etc/chronovault/agent.env

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable chronovault-agent
sudo systemctl start chronovault-agent
```

### Docker Install

For containerized deployments:

```bash
docker run -d \
  --name chronovault-agent \
  --restart unless-stopped \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /var/lib/chronovault:/var/lib/chronovault \
  -v /etc/chronovault:/etc/chronovault \
  chronovault/agent:latest \
  run
```

---

## Platform-Specific Instructions

### Ubuntu/Debian

```bash
# Update package list
sudo apt update

# Install dependencies
sudo apt install -y curl wget gnupg2

# Quick install
curl -fsSL https://raw.githubusercontent.com/chronovault/chronovault/main/install-agent.sh | bash

# Or manual install
wget https://github.com/chronovault/chronovault/releases/download/v0.1.0/chronovault-agent-linux-amd64
chmod +x chronovault-agent-linux-amd64
sudo mv chronovault-agent-linux-amd64 /opt/chronovault/chronovault-agent

# Start service
sudo systemctl enable chronovault-agent
sudo systemctl start chronovault-agent
```

### CentOS/RHEL

```bash
# Install dependencies
sudo yum install -y curl wget

# Quick install
curl -fsSL https://raw.githubusercontent.com/chronovault/chronovault/main/install-agent.sh | bash

# Or manual install
wget https://github.com/chronovault/chronovault/releases/download/v0.1.0/chronovault-agent-linux-amd64
chmod +x chronovault-agent-linux-amd64
sudo mv chronovault-agent-linux-amd64 /opt/chronovault/chronovault-agent

# Start service
sudo systemctl enable chronovault-agent
sudo systemctl start chronovault-agent

# If firewall is enabled
sudo firewall-cmd --permanent --add-port=8081/tcp
sudo firewall-cmd --reload
```

### Alpine Linux

```bash
# Install dependencies
apk add --no-cache curl wget bash

# Quick install
curl -fsSL https://raw.githubusercontent.com/chronovault/chronovault/main/install-agent.sh | bash

# Or manual install
wget https://github.com/chronovault/chronovault/releases/download/v0.1.0/chronovault-agent-linux-amd64
chmod +x chronovault-agent-linux-amd64
mv chronovault-agent-linux-amd64 /opt/chronovault/chronovault-agent

# Create OpenRC service
cat > /etc/init.d/chronovault-agent << 'EOF'
#!/sbin/openrc-run

name="chronovault-agent"
description="ChronoVault Agent Service"

command="/opt/chronovault/chronovault-agent"
command_args="run"
command_background=true
pidfile="/run/chronovault-agent.pid"
output_log="/var/log/chronovault-agent.log"
error_log="/var/log/chronovault-agent.log"

depend() {
    need net
    after docker
}
EOF

chmod +x /etc/init.d/chronovault-agent
rc-update add chronovault-agent default
rc-service chronovault-agent start
```

### Amazon Linux

```bash
# Install dependencies
sudo yum install -y curl wget

# Quick install
curl -fsSL https://raw.githubusercontent.com/chronovault/chronovault/main/install-agent.sh | bash

# Or manual install
wget https://github.com/chronovault/chronovault/releases/download/v0.1.0/chronovault-agent-linux-amd64
chmod +x chronovault-agent-linux-amd64
sudo mv chronovault-agent-linux-amd64 /opt/chronovault/chronovault-agent

# Start service
sudo systemctl enable chronovault-agent
sudo systemctl start chronovault-agent

# If using AWS Security Group
# Allow outbound HTTPS (443) to ChronoVault Backend
```

---

## Configuration

### Configuration File

Location: `/etc/chronovault/agent.yaml`

```yaml
server:
  # ChronoVault Backend URL
  backend_url: "http://your-chronovault-server:8080"
  
  # API Key for authentication
  api_key: "your-api-key"
  
  # Server name (displayed in ChronoVault UI)
  server_name: "web-server-01"

heartbeat:
  # Heartbeat interval in seconds
  interval: 60

scanner:
  # Which collectors to enable
  enabled_collectors:
    - packages    # Installed packages (apt/yum/apk)
    - services    # Systemd services
    - ports       # Open ports
    - docker      # Docker containers
    - configs     # /etc file hashes
    - crontab     # Scheduled tasks
  
  # Directories to scan for config changes
  config_paths:
    - /etc/nginx
    - /etc/ssh
    - /etc/docker
    - /etc/chronovault
  
  # Timeout per collector in seconds
  timeout: 10

restic:
  # Auto-install Restic if not found
  auto_install: true
  
  # Restic repository path
  repository: "/var/lib/chronovault/restic"
```

### Environment Variables

You can also configure via environment variables:

```bash
# /etc/chronovault/agent.env
CV_BACKEND_URL=http://your-chronovault-server:8080
CV_API_KEY=your-api-key
CV_SERVER_NAME=web-server-01
CV_HEARTBEAT_INTERVAL=60
```

### API Key Generation

1. Log in to ChronoVault Web UI
2. Go to **Settings** → **API Keys**
3. Click **Generate Key**
4. Enter a name (e.g., `web-server-01`)
5. Select scope: `Agent`
6. Copy the generated key

> ⚠️ The key is shown only once! Save it securely.

---

## Systemd Service

### Service Commands

```bash
# Start the agent
sudo systemctl start chronovault-agent

# Stop the agent
sudo systemctl stop chronovault-agent

# Restart the agent
sudo systemctl restart chronovault-agent

# Check status
sudo systemctl status chronovault-agent

# View logs
sudo journalctl -u chronovault-agent -f

# View logs (last 100 lines)
sudo journalctl -u chronovault-agent -n 100

# Enable auto-start on boot
sudo systemctl enable chronovault-agent

# Disable auto-start
sudo systemctl disable chronovault-agent
```

### Service Configuration

Edit `/etc/systemd/system/chronovault-agent.service`:

```ini
[Unit]
Description=ChronoVault Agent
After=network.target docker.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/chronovault
ExecStart=/opt/chronovault/chronovault-agent run
Restart=always
RestartSec=10
# Add resource limits if needed
# LimitNOFILE=65536
# CPUQuota=50%
# MemoryMax=256M

[Install]
WantedBy=multi-user.target
```

After editing:
```bash
sudo systemctl daemon-reload
sudo systemctl restart chronovault-agent
```

---

## Verification

### Check Agent Status

```bash
# Check if Agent is running
sudo systemctl status chronovault-agent

# Check Agent health endpoint
curl http://localhost:8081/health

# Expected output:
# {"status":"up","version":"0.1.0","server":"web-server-01"}
```

### Verify Registration

1. Log in to ChronoVault Web UI
2. Go to **Servers**
3. Your server should appear with status **Online**
4. Click on the server to view Agent details:
   - Agent version
   - Last heartbeat time
   - System info (OS, kernel, uptime)

### Test State Collection

```bash
# Manually trigger a state collection
/opt/chronovault/chronovault-agent scan

# Check logs for collection results
sudo journalctl -u chronovault-agent -n 50
```

---

## Troubleshooting

### Agent Won't Start

```bash
# Check logs for errors
sudo journalctl -u chronovault-agent -n 100

# Common issues:
# 1. Port 8081 already in use
sudo lsof -i :8081

# 2. Permission denied
sudo chmod +x /opt/chronovault/chronovault-agent

# 3. Configuration file not found
sudo ls -la /etc/chronovault/agent.yaml
```

### Can't Connect to Backend

```bash
# Test connectivity to Backend
curl -v http://your-chronovault-server:8080/actuator/health

# Check firewall
sudo iptables -L -n | grep 8080

# Verify API key
curl -H "X-API-Key: your-api-key" http://your-chronovault-server:8080/api/v1/agent/version
```

### State Collection Fails

```bash
# Check if collectors are enabled
grep -A 10 "enabled_collectors" /etc/chronovault/agent.yaml

# Test individual collectors
/opt/chronovault/chronovault-agent scan --collector=packages
/opt/chronovault/chronovault-agent scan --collector=services

# Check Docker socket access
ls -la /var/run/docker.sock
```

### Restic Not Found

```bash
# Check if Restic is installed
which restic
restic version

# If not installed, enable auto-install in config
# Or install manually:
wget https://github.com/restic/restic/releases/download/v0.16.0/restic_0.16.0_linux_amd64.bz2
bunzip2 restic_0.16.0_linux_amd64.bz2
chmod +x restic_0.16.0_linux_amd64
sudo mv restic_0.16.0_linux_amd64 /usr/local/bin/restic
```

### High Resource Usage

```bash
# Check Agent resource usage
top -p $(pgrep chronovault-agent)

# Add resource limits to systemd service
sudo systemctl edit chronovault-agent

# Add:
# [Service]
# CPUQuota=50%
# MemoryMax=256M

# Restart to apply
sudo systemctl restart chronovault-agent
```

---

## Upgrading

### Upgrade Script

```bash
curl -fsSL https://raw.githubusercontent.com/chronovault/chronovault/main/upgrade-agent.sh | bash
```

### Manual Upgrade

```bash
# Stop the agent
sudo systemctl stop chronovault-agent

# Download new version
VERSION="0.1.1"
wget https://github.com/chronovault/chronovault/releases/download/v${VERSION}/chronovault-agent-linux-amd64
chmod +x chronovault-agent-linux-amd64

# Replace binary
sudo mv chronovault-agent-linux-amd64 /opt/chronovault/chronovault-agent

# Start the agent
sudo systemctl start chronovault-agent

# Verify upgrade
curl http://localhost:8081/health
```

### Docker Upgrade

```bash
# Pull new image
docker pull chronovault/agent:latest

# Stop and remove old container
docker stop chronovault-agent
docker rm chronovault-agent

# Start new container
docker run -d \
  --name chronovault-agent \
  --restart unless-stopped \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /var/lib/chronovault:/var/lib/chronovault \
  -v /etc/chronovault:/etc/chronovault \
  chronovault/agent:latest \
  run
```

---

## Uninstalling

### Stop and Disable Service

```bash
sudo systemctl stop chronovault-agent
sudo systemctl disable chronovault-agent
```

### Remove Files

```bash
# Remove binary
sudo rm /opt/chronovault/chronovault-agent

# Remove configuration
sudo rm -rf /etc/chronovault

# Remove systemd service
sudo rm /etc/systemd/system/chronovault-agent.service
sudo systemctl daemon-reload

# Remove data (optional, contains backups)
sudo rm -rf /var/lib/chronovault
```

### Docker Uninstall

```bash
docker stop chronovault-agent
docker rm chronovault-agent
docker rmi chronovault/agent:latest
```

---

## Security Considerations

### API Key Security

- Store API keys securely (not in plaintext)
- Rotate keys periodically
- Use separate keys for each server

### Network Security

- Use HTTPS for Backend communication in production
- Restrict outbound access to only ChronoVault Backend
- Consider VPN for sensitive environments

### File Permissions

```bash
# Agent binary should be owned by root
sudo chown root:root /opt/chronovault/chronovault-agent
sudo chmod 755 /opt/chronovault/chronovault-agent

# Configuration should be readable only by root
sudo chown root:root /etc/chronovault/agent.yaml
sudo chmod 600 /etc/chronovault/agent.yaml
```

### Firewall Rules

```bash
# Allow outbound HTTPS to ChronoVault Backend
# (Assuming Backend is at 10.0.0.100)
sudo iptables -A OUTPUT -d 10.0.0.100 -p tcp --dport 8080 -j ACCEPT

# Allow outbound DNS
sudo iptables -A OUTPUT -p udp --dport 53 -j ACCEPT

# Allow outbound HTTPS
sudo iptables -A OUTPUT -p tcp --dport 443 -j ACCEPT
```
