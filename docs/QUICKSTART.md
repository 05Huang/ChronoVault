# ChronoVault Quick Start Guide

> 5 minutes from zero to first snapshot

---

## Prerequisites

- Docker & Docker Compose v2+ installed
- 4GB+ RAM
- 20GB+ disk space
- A Linux server to manage (Ubuntu/Debian/CentOS)
- SSH access to the target server

---

## Step 1: Start ChronoVault

```bash
# Clone the repository
git clone https://github.com/chronovault/chronovault.git
cd chronovault

# Generate security keys and create .env
cat > .env << EOF
POSTGRES_DB=chronovault
POSTGRES_USER=chronovault
POSTGRES_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -hex 32)
CHRONOVAULT_MASTER_KEY=$(openssl rand -hex 32)
CHRONOVAULT_RESTIC_PASSWORD=$(openssl rand -hex 32)
SPRING_PROFILES_ACTIVE=prod
CORS_ALLOWED_ORIGINS=http://localhost
EOF

# Start all services
docker compose up -d --build

# Wait for services to be ready (about 30 seconds)
docker compose logs -f backend
# Press Ctrl+C when you see "Started ChronovaultBackendApplication"
```

Open your browser and go to **http://localhost**

---

## Step 2: Create Your Account

1. Click **Register** on the login page
2. Fill in the registration form:
   - Name: `Admin`
   - Email: `admin@chronovault.com`
   - Password: `Admin123!`
3. Click **Register**
4. You'll be redirected to the Dashboard

> Note: The first registered user automatically gets OWNER role.

---

## Step 3: Add a Server

1. Navigate to **Servers** in the sidebar
2. Click **Add Server**
3. Fill in the server details:
   - Name: `web-server-01`
   - IP Address: `192.168.1.100` (your server's IP)
   - OS: `Ubuntu 22.04`
4. Configure SSH connection:
   - Port: `22`
   - Username: `root`
   - Auth Method: `KEY` (recommended) or `PASSWORD`
   - Paste your SSH private key or enter password
5. Click **Test Connection** to verify SSH access
6. If successful, click **Save**

### SSH Key Setup (if needed)

If you don't have SSH keys set up:

```bash
# Generate SSH key pair
ssh-keygen -t ed25519 -C "chronovault"

# Copy public key to target server
ssh-copy-id -i ~/.ssh/id_ed25519.pub user@192.168.1.100
```

---

## Step 4: Create Your First Snapshot

1. Go to **Snapshots** in the sidebar
2. Click **Create Snapshot**
3. Select your server: `web-server-01`
4. Configure the snapshot:
   - Type: `FULL` (captures everything)
   - Title: `Initial Backup`
   - Note: `First snapshot of web server`
5. (Optional) Add paths to backup:
   - `/etc` (system configs)
   - `/opt` (applications)
   - `/var/www` (web files)
6. Click **Create Snapshot**

The snapshot creation will take a few minutes depending on the amount of data.

---

## Step 5: View Your Snapshot

Once the snapshot completes:

1. Click on the snapshot in the list
2. View the **Details** tab:
   - Snapshot ID, status, size
   - Creation time
   - Tags
3. View the **State** tab (this is the magic!):
   - **Packages**: All installed packages with versions
   - **Services**: Systemd services status
   - **Ports**: Open ports and processes
   - **Docker**: Container status (if Docker is installed)
   - **Configs**: /etc file hashes
   - **Crontab**: Scheduled tasks

---

## Step 6: Compare Snapshots (Diff)

After making changes to your server:

1. Install an update on your server:
   ```bash
   # On your target server
   sudo apt update && sudo apt upgrade -y
   ```

2. Create another snapshot in ChronoVault

3. Go to **Snapshots** and select two snapshots
4. Click **Compare** to see the diff

You'll see exactly what changed:
- New packages installed
- Packages upgraded (old version → new version)
- Services that changed status
- New ports opened
- Config files modified

---

## Step 7: Rollback (Optional)

If something goes wrong:

1. Go to **Snapshots**
2. Find the snapshot you want to restore
3. Click **Rollback**
4. Confirm the rollback operation

ChronoVault will restore:
- Files to their previous state
- Packages to their previous versions
- Services to their previous status

---

## Default Credentials

| Field | Value |
|-------|-------|
| Email | admin@chronovault.com |
| Password | Admin123! |

> Change these credentials immediately in production!

---

## What's Next?

- [Architecture Overview](ARCHITECTURE.md) - Understand the system design
- [User Guide](USER_GUIDE.md) - Detailed feature documentation
- [Agent Installation](AGENT_INSTALLATION.md) - Deploy agents to more servers
- [API Reference](API_REFERENCE.md) - REST API documentation
- [Troubleshooting](TROUBLESHOOTING.md) - Common issues and solutions
- [Deployment Guide](DEPLOYMENT.md) - Production deployment

---

## Troubleshooting

### SSH Connection Failed

1. Verify server IP and port are correct
2. Check SSH service is running: `systemctl status sshd`
3. Verify key format is correct (OpenSSH format)
4. Check firewall allows SSH: `sudo ufw allow ssh`

### Snapshot Creation Failed

1. Verify Agent is running on target server
2. Check disk space: `df -h /`
3. Verify storage target configuration
4. Check Backend logs: `docker compose logs backend`

### Agent Installation Failed

1. Verify Backend service is running
2. Verify Agent can reach Backend API
3. Check API Key is correct
4. Check Agent logs: `journalctl -u chronovault-agent -f`

### Can't Access Web Interface

1. Verify services are running: `docker compose ps`
2. Check port 80 is not in use: `sudo lsof -i :80`
3. Review frontend logs: `docker compose logs frontend`