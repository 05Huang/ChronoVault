# ChronoVault User Guide

> Complete guide to managing server state with ChronoVault

---

## Table of Contents

- [Dashboard](#dashboard)
- [Server Management](#server-management)
- [Snapshots](#snapshots)
- [State Diff](#state-diff)
- [Timeline](#timeline)
- [Rollback](#rollback)
- [Branching](#branching)
- [Stash](#stash)
- [Alerts](#alerts)
- [Storage](#storage)
- [Settings](#settings)

---

## Dashboard

The Dashboard provides an overview of your server infrastructure.

### Overview Cards

- **Total Servers**: Number of registered servers
- **Active Servers**: Servers with recent Agent heartbeats
- **Total Snapshots**: All snapshots across all servers
- **Alerts Today**: New alerts in the last 24 hours
- **Recovery Rate**: Percentage of successful recoveries

### Server Status Panel

Shows each server's health status:
- Green: Server online, last snapshot < 24 hours
- Yellow: Server online, last snapshot > 24 hours (stale)
- Red: Server offline (no heartbeat)

### Activity Trend

Chart showing snapshot creation and alert trends over the past 7 days.

### Quick Actions

- **Create Snapshot**: Trigger a snapshot on any server
- **Add Server**: Register a new server
- **View Alerts**: See recent alerts and warnings

---

## Server Management

### Adding a Server

1. Navigate to **Servers** in the sidebar
2. Click **Add Server**
3. Fill in the server details:
   - **Name**: A descriptive name (e.g., `web-server-01`)
   - **IP Address**: Server's IP or hostname
   - **OS**: Operating system (Ubuntu, CentOS, Debian)
4. Configure SSH connection:
   - **Port**: SSH port (default: 22)
   - **Username**: SSH user (e.g., `root`)
   - **Auth Method**: `KEY` (recommended) or `PASSWORD`
   - **Credential**: SSH private key or password
5. Click **Test Connection** to verify
6. Click **Save** to add the server

### Server Details

Click on a server to view:

#### Info Tab
- Server name, IP, OS
- SSH configuration
- Auto-snapshot toggle
- Agent version and status

#### Snapshots Tab
- List of all snapshots for this server
- Create new snapshot button
- Filter by date, status, tags

#### Containers Tab
- Docker containers on the server
- Container status (running, stopped, etc.)
- Resource usage (CPU, memory, disk)

#### Volumes Tab
- Docker volumes
- Volume size and mount points

#### Logs Tab
- Server log entries
- Filter by level (INFO, WARN, ERROR)

### SSH Key Rotation

For enhanced security, periodically rotate SSH keys:

1. Go to **Server Details** → **SSH Config**
2. Click **Rotate Key**
3. Follow the instructions to update the key on the target server

### Auto-Snapshot

Enable automatic snapshots for critical servers:

1. Go to **Server Details**
2. Toggle **Auto-Snapshot** to ON
3. Configure snapshot schedule (via Scheduled Backups)

### Installing Agent

The Agent enables state collection on the target server:

1. Go to **Server Details**
2. Click **Install Agent**
3. Follow the installation instructions
4. Verify Agent is online in the server health status

---

## Snapshots

### Creating a Snapshot

1. Navigate to **Snapshots** in the sidebar
2. Click **Create Snapshot**
3. Select the server
4. Configure:
   - **Type**: FULL (complete backup) or INCREMENTAL (changes only)
   - **Title**: Descriptive name (e.g., `Before nginx upgrade`)
   - **Note**: Additional context
5. (Optional) Add paths to backup:
   - Click **Add Path** to include specific directories
   - Default paths can be configured in server settings
6. Click **Create Snapshot**

### Snapshot Status

- **QUEUED**: Waiting to start
- **RUNNING**: Backup in progress
- **SUCCESS**: Backup completed successfully
- **FAILED**: Backup failed (check logs for details)
- **WARNING**: Backup completed with warnings

### Snapshot Details

Click on a snapshot to view:

#### Overview
- Snapshot ID, status, size
- Creation time
- Server name
- Tags

#### State Tab (Core Feature)

This is where ChronoVault differs from traditional backup tools:

**Packages**
- List of installed packages with versions
- Package manager (apt, yum, apk, etc.)
- Highlights: additions, removals, upgrades

**Services**
- Systemd services status
- Enabled/disabled state
- Process ID (PID)

**Ports**
- Open ports on the server
- Protocol (TCP/UDP)
- Associated process

**Docker**
- Running containers
- Container images and status
- Port mappings
- Docker Compose files

**Configs**
- /etc configuration files
- SHA-256 hashes for change detection
- File sizes

**Crontab**
- Scheduled tasks
- User, schedule, command

### Snapshot Tags

Organize snapshots with tags:

1. Go to **Snapshots**
2. Select snapshots (checkbox)
3. Click **Add Tag**
4. Enter tag name and color

Common tags:
- `production` - Production snapshots
- `pre-upgrade` - Before system updates
- `baseline` - Reference snapshots
- `daily` - Automated daily backups

### Comparing Snapshots (Diff)

1. Go to **Snapshots**
2. Select two snapshots
3. Click **Compare**

The diff view shows:
- **Packages**: Added, removed, upgraded
- **Services**: Status changes
- **Ports**: New/closed ports
- **Configs**: Modified files with unified diff
- **Docker**: Container changes

### Downloading Snapshot Files

1. Go to **Snapshot Details**
2. Click **Files** tab
3. Browse the file tree
4. Click on a file to download

### Verifying Snapshot Integrity

1. Go to **Snapshot Details**
2. Click **Verify**
3. ChronoVault checks the Restic repository integrity

---

## State Diff

The State Diff is ChronoVault's core differentiator.

### Understanding the Diff View

When comparing two snapshots, you'll see:

#### Package Changes

```
Added:
  + curl 7.88.1 (apt)
  + jq 1.6 (apt)

Removed:
  - wget 1.21 (apt)

Upgraded:
  ~ nginx 1.22.0 → 1.24.0 (apt)
  ~ docker.io 20.10 → 24.0 (apt)
```

#### Service Changes

```
Changed:
  ~ nginx: inactive → active
  ~ docker: active → inactive
```

#### Port Changes

```
New Ports:
  + 8080/tcp (node)

Closed Ports:
  - 3000/tcp (node)
```

#### Config Changes

Shows unified diff for modified config files:
```diff
--- a/etc/nginx/nginx.conf
+++ b/etc/nginx/nginx.conf
@@ -10,7 +10,7 @@
-    worker_processes 1;
+    worker_processes 4;
```

### Using the Diff for Troubleshooting

1. **Identify what changed**: See exactly which packages/services/configs changed
2. **Find the root cause**: Correlate changes with incidents
3. **Rollback selectively**: Revert only the problematic changes

---

## Timeline

The Timeline view shows snapshot history like a Git log.

### Timeline Features

- **Chronological view**: Snapshots displayed in order
- **Change summaries**: Brief description of what changed
- **Branch visualization**: See which branch each snapshot belongs to
- **Quick actions**: Create snapshot, compare, rollback from any point

### Navigation

- **Scroll**: Browse history
- **Click**: View snapshot details
- **Compare**: Select two points to diff
- **Branch**: Create a new branch from any snapshot

---

## Rollback

### Full Rollback

Restore the entire server to a previous state:

1. Go to **Snapshots**
2. Find the target snapshot
3. Click **Rollback**
4. Confirm the operation

What happens:
- Files are restored from Restic backup
- Packages are reverted to snapshot versions
- Services are restored to snapshot status

### Selective Rollback

Roll back specific changes:

1. Go to **Snapshot Diff**
2. Find the change you want to revert
3. Click **Rollback** on that specific item

Supported selective rollbacks:
- **Config files**: Extract from backup and restore
- **Packages**: Install specific version
- **Services**: Re-enable or restart

### Rollback Preview

Before rolling back, preview what will change:

1. Go to **Snapshot Details**
2. Click **Rollback Preview**
3. Review the list of changes

### Revert

Undo a specific snapshot's changes without full rollback:

1. Go to **Snapshot Details**
2. Click **Revert**
3. Select which changes to undo

---

## Branching

Create parallel state tracks for experimentation.

### Creating a Branch

1. Go to **Server Details** → **Branches**
2. Click **Create Branch**
3. Enter branch name (e.g., `testing-upgrade`)
4. Select base snapshot (optional)
5. Click **Create**

### Switching Branches

1. Go to **Server Details** → **Branches**
2. Find the target branch
3. Click **Switch**

### Merging Branches

1. Go to **Server Details** → **Branches**
2. Click **Merge**
3. Select source and target branches
4. Review changes
5. Click **Merge**

### Deleting a Branch

1. Go to **Server Details** → **Branches**
2. Find the branch
3. Click **Delete**

---

## Stash

Temporarily shelve changes for quick recovery.

### Creating a Stash

1. Go to **Server Details** → **Stash**
2. Click **Create Stash**
3. Enter a note (optional)
4. Click **Create**

### Popping a Stash

1. Go to **Server Details** → **Stash**
2. Find the stash
3. Click **Pop**

### Discarding a Stash

1. Go to **Server Details** → **Stash**
2. Find the stash
3. Click **Delete**

---

## Alerts

### Alert Types

- **Critical**: Immediate attention required (SSH down, disk full)
- **Warning**: Potential issues (high disk usage, stale snapshots)
- **Info**: Informational (new agent version available)

### Alert Rules

Configure when alerts trigger:

1. Go to **Alerts** → **Rules**
2. Click **Add Rule**
3. Configure:
   - **Name**: Rule description
   - **Metric**: What to monitor (disk_usage, snapshot_age, etc.)
   - **Threshold**: Trigger value
   - **Severity**: Alert level

### Dismissing Alerts

1. Go to **Alerts**
2. Find the alert
3. Click **Dismiss**

---

## Storage

### Storage Targets

Manage backup destinations:

1. Go to **Storage**
2. Click **Add Storage**
3. Select type:
   - **Local**: Server filesystem
   - **S3**: Amazon S3 or compatible
   - **OSS**: Alibaba Cloud OSS
   - **WebDAV**: WebDAV server
4. Configure credentials
5. Click **Save**

### Storage Health

Monitor storage usage:

- **Used**: Space consumed by backups
- **Total**: Available space
- **Usage %**: Capacity percentage
- **Status**: HEALTHY, WARNING, CRITICAL

### Storage Replication

Copy snapshots across storage targets:

1. Go to **Snapshot Details**
2. Click **Replicate**
3. Select target storage
4. Click **Replicate**

---

## Settings

### API Keys

Manage API keys for Agent authentication:

1. Go to **Settings** → **API Keys**
2. Click **Generate Key**
3. Enter name and scope
4. Copy the key (shown only once!)

### Audit Logs

View all user actions:

1. Go to **Settings** → **Audit Logs**
2. Filter by:
   - User
   - Action type
   - Time range
   - Resource type

### AI Configuration

Configure AI features:

1. Go to **Settings** → **AI Config**
2. Enable/disable AI analysis
3. Configure API endpoint (MiMo or OpenAI compatible)

### Scheduled Backups

Automate snapshot creation:

1. Go to **Settings** → **Scheduled Backups**
2. Click **Add Schedule**
3. Configure:
   - **Server**: Target server
   - **Cron Expression**: Schedule (e.g., `0 2 * * *` for daily at 2 AM)
   - **Paths**: Directories to backup
   - **Storage**: Target storage

### Retention Policies

Automatically clean old snapshots:

1. Go to **Settings** → **Retention Policies**
2. Click **Add Policy**
3. Configure:
   - **Server**: Apply to all or specific servers
   - **Keep Daily**: Number of daily snapshots
   - **Keep Weekly**: Number of weekly snapshots
   - **Keep Monthly**: Number of monthly snapshots

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `j` / `k` | Navigate up/down in lists |
| `Enter` | Open selected item |
| `Esc` | Close modal / Go back |
| `Ctrl+N` | Create new snapshot |
| `Ctrl+F` | Search |
| `?` | Show keyboard shortcuts |

---

## Tips and Best Practices

### Snapshot Naming

Use descriptive titles:
- ✅ `Before nginx upgrade to 1.24`
- ✅ `Production daily backup 2026-06-06`
- ❌ `backup1`
- ❌ `test`

### Tagging Strategy

Use consistent tags:
- `daily` / `weekly` / `monthly` - Frequency
- `production` / `staging` / `dev` - Environment
- `pre-upgrade` / `post-migration` - Context

### Snapshot Frequency

Recommended snapshot schedules:
- **Production**: Every 6 hours or daily
- **Staging**: Daily
- **Development**: On-demand

### Storage Management

- Enable retention policies to avoid disk space issues
- Use multiple storage targets for redundancy
- Monitor storage health regularly

### Security

- Rotate SSH keys periodically
- Use API keys for Agent authentication
- Review audit logs regularly
- Enable alerts for suspicious activity
