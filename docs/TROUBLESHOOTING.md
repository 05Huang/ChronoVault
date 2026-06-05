# ChronoVault Troubleshooting Guide

> Common issues and solutions for ChronoVault

---

## Table of Contents

- [Installation Issues](#installation-issues)
- [SSH Connection Issues](#ssh-connection-issues)
- [Backup Issues](#backup-issues)
- [Restore/Rollback Issues](#restorerollback-issues)
- [Performance Issues](#performance-issues)
- [Database Issues](#database-issues)
- [Frontend Issues](#frontend-issues)
- [Agent Issues](#agent-issues)
- [Networking Issues](#networking-issues)
- [Security Issues](#security-issues)

---

## Installation Issues

### Docker Compose Fails to Start

**Symptoms:**
```
Error: No such service: postgres
```

**Solutions:**
1. Ensure Docker and Docker Compose are installed:
   ```bash
   docker --version
   docker compose version
   ```

2. Check docker-compose.yml syntax:
   ```bash
   docker compose config
   ```

3. Ensure you're in the correct directory:
   ```bash
   cd /path/to/chronovault
   docker compose up -d
   ```

### Port Already in Use

**Symptoms:**
```
Error: Bind for 0.0.0.0:5432 failed: port is already allocated
```

**Solutions:**
1. Find the process using the port:
   ```bash
   sudo lsof -i :5432
   ```

2. Stop the conflicting service:
   ```bash
   sudo systemctl stop postgresql
   ```

3. Or change the port in docker-compose.yml:
   ```yaml
   ports:
     - "5433:5432"  # Use 5433 instead
   ```

### Insufficient Disk Space

**Symptoms:**
```
Error: no space left on device
```

**Solutions:**
1. Check disk usage:
   ```bash
   df -h
   ```

2. Clean up Docker resources:
   ```bash
   docker system prune -a
   ```

3. Remove old logs:
   ```bash
   sudo journalctl --vacuum-size=100M
   ```

---

## SSH Connection Issues

### Connection Refused

**Symptoms:**
- "Connection refused" error when testing SSH
- Agent cannot connect to target server

**Solutions:**
1. Verify SSH service is running:
   ```bash
   sudo systemctl status sshd
   sudo systemctl start sshd
   ```

2. Check SSH port:
   ```bash
   sudo netstat -tlnp | grep ssh
   ```

3. Verify firewall rules:
   ```bash
   sudo ufw status
   sudo ufw allow ssh
   ```

### Permission Denied (Publickey)

**Symptoms:**
```
Permission denied (publickey)
```

**Solutions:**
1. Verify SSH key permissions:
   ```bash
   chmod 700 ~/.ssh
   chmod 600 ~/.ssh/id_ed25519
   ```

2. Copy public key to server:
   ```bash
   ssh-copy-id -i ~/.ssh/id_ed25519.pub user@server
   ```

3. Check SSH config:
   ```bash
   ssh -v user@server
   ```

### Host Key Verification Failed

**Symptoms:**
```
Host key verification failed
```

**Solutions:**
1. Add server to known_hosts:
   ```bash
   ssh-keyscan -H server >> ~/.ssh/known_hosts
   ```

2. Or disable strict checking (not recommended for production):
   ```bash
   ssh -o StrictHostKeyChecking=no user@server
   ```

### Connection Timeout

**Symptoms:**
```
Connection timed out
```

**Solutions:**
1. Check network connectivity:
   ```bash
   ping server
   traceroute server
   ```

2. Verify firewall allows SSH:
   ```bash
   sudo iptables -L -n | grep 22
   ```

3. Check if server is reachable:
   ```bash
   telnet server 22
   ```

### Too Many Authentication Failures

**Symptoms:**
```
Too many authentication failures
```

**Solutions:**
1. Specify the correct key explicitly:
   ```bash
   ssh -i ~/.ssh/id_ed25519 user@server
   ```

2. Check SSH agent:
   ```bash
   ssh-add -l
   ssh-add ~/.ssh/id_ed25519
   ```

---

## Backup Issues

### Restic Not Found

**Symptoms:**
```
Error: restic: command not found
```

**Solutions:**
1. Install Restic:
   ```bash
   # Ubuntu/Debian
   sudo apt install restic
   
   # CentOS/RHEL
   sudo yum install restic
   
   # Manual install
   wget https://github.com/restic/restic/releases/download/v0.16.0/restic_0.16.0_linux_amd64.bz2
   bunzip2 restic_0.16.0_linux_amd64.bz2
   chmod +x restic_0.16.0_linux_amd64
   sudo mv restic_0.16.0_linux_amd64 /usr/local/bin/restic
   ```

2. Or enable auto-install in Agent config:
   ```yaml
   restic:
     auto_install: true
   ```

### Backup Repository Already Initialized

**Symptoms:**
```
error: repository already initialized
```

**Solutions:**
1. This is normal - Restic repository is already set up
2. The Agent will skip initialization automatically
3. To reinitialize (WARNING: deletes existing backups):
   ```bash
   restic -r /var/lib/chronovault/restic init --remove
   ```

### Permission Denied During Backup

**Symptoms:**
```
permission denied
```

**Solutions:**
1. Ensure Agent runs as root:
   ```bash
   sudo systemctl edit chronovault-agent
   # Ensure: User=root
   ```

2. Check directory permissions:
   ```bash
   sudo chown -R root:root /var/lib/chronovault
   ```

### Disk Space Insufficient for Backup

**Symptoms:**
```
error: write: no space left on device
```

**Solutions:**
1. Check disk space:
   ```bash
   df -h /var/lib/chronovault
   ```

2. Clean up old backups:
   ```bash
   restic -r /var/lib/chronovault/restic forget --keep-daily 7 --prune
   ```

3. Check for large files:
   ```bash
   du -sh /var/lib/chronovault/restic/*
   ```

### Backup Stuck or Very Slow

**Symptoms:**
- Backup takes hours to complete
- Progress doesn't update

**Solutions:**
1. Check network bandwidth:
   ```bash
   speedtest-cli
   ```

2. Check disk I/O:
   ```bash
   iostat -x 1
   ```

3. Reduce backup scope:
   - Backup only essential directories
   - Exclude large, unnecessary files

4. Check Agent logs:
   ```bash
   sudo journalctl -u chronovault-agent -f
   ```

### Restic Repository Corrupted

**Symptoms:**
```
error: verify integrity of repository failed
```

**Solutions:**
1. Check repository integrity:
   ```bash
   restic -r /var/lib/chronovault/restic check
   ```

2. Try to repair:
   ```bash
   restic -r /var/lib/chronovault/restic check --read-data
   ```

3. If corruption is severe, reinitialize:
   ```bash
   restic -r /var/lib/chronovault/restic init --remove
   ```

---

## Restore/Rollback Issues

### Restore Failed - File Not Found

**Symptoms:**
```
error: file not found in snapshot
```

**Solutions:**
1. Verify the file exists in the snapshot:
   ```bash
   restic -r /var/lib/chronovault/restic ls <snapshot-id> /path/to/file
   ```

2. Check if the file was in the backup paths:
   - Ensure the file's directory was included in backup configuration

### Permission Denied During Restore

**Symptoms:**
```
permission denied: /path/to/file
```

**Solutions:**
1. Ensure Agent has write permissions:
   ```bash
   sudo chown -R root:root /path/to/file
   sudo chmod 755 /path/to/file
   ```

2. Check if file is immutable:
   ```bash
   lsattr /path/to/file
   sudo chattr -i /path/to/file
   ```

### Service Not Starting After Rollback

**Symptoms:**
- Service fails to start after package rollback
- Configuration mismatch

**Solutions:**
1. Check service logs:
   ```bash
   sudo journalctl -u <service-name> -n 50
   ```

2. Verify configuration files were restored:
   ```bash
   ls -la /etc/<service>/
   ```

3. Restart the service:
   ```bash
   sudo systemctl restart <service-name>
   ```

### Package Version Not Available

**Symptoms:**
```
E: Version '1.22.0-1' for 'nginx' was not found
```

**Solutions:**
1. Check available versions:
   ```bash
   apt list nginx -a 2>/dev/null
   yum --showduplicates list nginx
   ```

2. The exact version may not be available in repositories
3. Consider using a different snapshot with available version

---

## Performance Issues

### Dashboard Loading Slowly

**Symptoms:**
- Dashboard takes > 5 seconds to load
- API responses are slow

**Solutions:**
1. Check database performance:
   ```sql
   -- Check slow queries
   SELECT query, mean_time, calls 
   FROM pg_stat_statements 
   ORDER BY mean_time DESC 
   LIMIT 10;
   ```

2. Add database indexes:
   ```sql
   CREATE INDEX idx_snapshots_created_at ON snapshots(created_at);
   CREATE INDEX idx_snapshots_server_id ON snapshots(server_id);
   ```

3. Increase JVM memory:
   ```bash
   export JAVA_OPTS="-Xmx1g -Xms512m"
   ```

### High Memory Usage

**Symptoms:**
- Backend or Agent using excessive memory
- OOM errors in logs

**Solutions:**
1. Check memory usage:
   ```bash
   free -h
   top -o %MEM
   ```

2. Limit container memory (Docker):
   ```yaml
   deploy:
     resources:
       limits:
         memory: 512M
   ```

3. Adjust JVM heap size:
   ```bash
   export JAVA_OPTS="-Xmx512m"
   ```

### High CPU Usage

**Symptoms:**
- Agent or Backend using 100% CPU
- System becomes unresponsive

**Solutions:**
1. Check CPU usage:
   ```bash
   top -o %CPU
   ```

2. Identify the process:
   ```bash
   ps aux | grep chronovault
   ```

3. Reduce concurrent operations:
   - Limit snapshot parallelism
   - Schedule heavy tasks during off-hours

---

## Database Issues

### Connection Refused

**Symptoms:**
```
Connection refused: connect
```

**Solutions:**
1. Check PostgreSQL status:
   ```bash
   sudo systemctl status postgresql
   sudo systemctl start postgresql
   ```

2. Verify connection settings:
   ```bash
   psql -h localhost -U chronovault -d chronovault
   ```

3. Check pg_hba.conf:
   ```bash
   sudo grep -v "^#" /etc/postgresql/15/main/pg_hba.conf | grep -v "^$"
   ```

### Authentication Failed

**Symptoms:**
```
FATAL: password authentication failed for user "chronovault"
```

**Solutions:**
1. Reset user password:
   ```sql
   ALTER USER chronovault WITH PASSWORD 'new-password';
   ```

2. Update .env file with new password

3. Restart Backend service

### Database Disk Full

**Symptoms:**
```
ERROR: could not extend file
```

**Solutions:**
1. Check disk space:
   ```bash
   df -h /var/lib/postgresql
   ```

2. Clean up old data:
   ```sql
   -- Delete old audit logs
   DELETE FROM audit_logs WHERE created_at < NOW() - INTERVAL '90 days';
   
   -- Vacuum to reclaim space
   VACUUM FULL;
   ```

3. Add more disk space or move to larger volume

### Migration Failed

**Symptoms:**
```
Migration failed: V42__description.sql
```

**Solutions:**
1. Check Flyway status:
   ```bash
   docker compose exec backend java -jar app.jar info
   ```

2. Manually apply migration:
   ```bash
   psql -h localhost -U chronovault -d chronovault -f V42__description.sql
   ```

3. Mark migration as applied:
   ```sql
   INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
   VALUES (42, '42', 'description', 'SQL', 'V42__description.sql', 12345, 'admin', 100, true);
   ```

---

## Frontend Issues

### White Screen / Blank Page

**Symptoms:**
- Browser shows blank page
- No errors in console

**Solutions:**
1. Check browser console (F12)
2. Verify Backend is running:
   ```bash
   curl http://localhost:8080/actuator/health
   ```
3. Clear browser cache:
   - Chrome: Ctrl+Shift+Delete
   - Firefox: Ctrl+Shift+Delete

### API 404 Errors

**Symptoms:**
```
GET http://localhost:8080/api/servers 404
```

**Solutions:**
1. Check API base path:
   - Correct: `/api/v1/servers`
   - Wrong: `/api/servers`

2. Verify Backend routes:
   ```bash
   curl http://localhost:8080/actuator/mappings
   ```

### WebSocket Connection Failed

**Symptoms:**
```
WebSocket connection to 'ws://localhost:8080/ws' failed
```

**Solutions:**
1. Check WebSocket endpoint:
   ```bash
   curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" http://localhost:8080/ws
   ```

2. Verify nginx proxy config (if using):
   ```nginx
   location /ws/ {
       proxy_pass http://backend:8080;
       proxy_http_version 1.1;
       proxy_set_header Upgrade $http_upgrade;
       proxy_set_header Connection "upgrade";
   }
   ```

### Session Expired Immediately

**Symptoms:**
- Logged out right after login
- "Unauthorized" errors

**Solutions:**
1. Check JWT secret:
   - Must be at least 32 characters
   - Must be the same across restarts

2. Verify system clock:
   ```bash
   date
   timedatectl status
   ```

---

## Agent Issues

### Agent Not Starting

**Symptoms:**
- Agent service fails to start
- "chronovault-agent: command not found"

**Solutions:**
1. Check binary exists:
   ```bash
   ls -la /opt/chronovault/chronovault-agent
   ```

2. Check permissions:
   ```bash
   sudo chmod +x /opt/chronovault/chronovault-agent
   ```

3. Check service status:
   ```bash
   sudo systemctl status chronovault-agent
   sudo journalctl -u chronovault-agent -n 50
   ```

### Agent Offline in UI

**Symptoms:**
- Server shows as "Offline" in ChronoVault
- No recent heartbeats

**Solutions:**
1. Check Agent logs:
   ```bash
   sudo journalctl -u chronovault-agent -f
   ```

2. Test Backend connectivity:
   ```bash
   curl http://your-backend:8080/actuator/health
   ```

3. Restart Agent:
   ```bash
   sudo systemctl restart chronovault-agent
   ```

### Agent Version Mismatch

**Symptoms:**
```
Warning: Agent version mismatch
```

**Solutions:**
1. Check current version:
   ```bash
   /opt/chronovault/chronovault-agent version
   ```

2. Upgrade Agent:
   ```bash
   # Follow upgrade instructions in AGENT_INSTALLATION.md
   ```

### State Collection Timeout

**Symptoms:**
- Snapshot state collection fails
- "context deadline exceeded"

**Solutions:**
1. Increase timeout in Agent config:
   ```yaml
   scanner:
     timeout: 30  # Increase from 10 to 30 seconds
   ```

2. Reduce number of collectors:
   ```yaml
   scanner:
     enabled_collectors:
       - packages
       - services
       # Remove slow collectors like docker
   ```

3. Check system load:
   ```bash
   uptime
   top -bn1 | head -20
   ```

---

## Networking Issues

### Cannot Reach Backend from Agent

**Symptoms:**
- Agent cannot register
- Heartbeats not received

**Solutions:**
1. Test connectivity:
   ```bash
   curl -v http://backend-ip:8080/actuator/health
   ```

2. Check firewall rules:
   ```bash
   # On Agent server
   sudo iptables -L -n | grep 8080
   sudo ufw status
   ```

3. Verify Backend is accessible:
   ```bash
   # From Agent server
   telnet backend-ip 8080
   ```

### CORS Errors

**Symptoms:**
```
Access to XMLHttpRequest blocked by CORS policy
```

**Solutions:**
1. Update CORS configuration:
   ```yaml
   chronovault:
     cors:
       allowed-origins: http://localhost:5173,http://your-domain.com
   ```

2. Or set environment variable:
   ```bash
   CORS_ALLOWED_ORIGINS=http://localhost:5173
   ```

### DNS Resolution Failed

**Symptoms:**
```
Temporary failure in name resolution
```

**Solutions:**
1. Check DNS configuration:
   ```bash
   cat /etc/resolv.conf
   ```

2. Test DNS resolution:
   ```bash
   nslookup your-backend.com
   dig your-backend.com
   ```

3. Add DNS server:
   ```bash
   echo "nameserver 8.8.8.8" | sudo tee -a /etc/resolv.conf
   ```

---

## Security Issues

### JWT Token Invalid

**Symptoms:**
```
Invalid JWT token
```

**Solutions:**
1. Verify JWT secret:
   - Must be at least 32 characters
   - Must match across all instances

2. Check token expiration:
   ```bash
   # Decode JWT
   echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .
   ```

3. Generate new secret:
   ```bash
   openssl rand -hex 32
   ```

### Encryption Key Invalid

**Symptoms:**
```
Failed to decrypt credentials
```

**Solutions:**
1. Verify master key:
   - Must be at least 32 characters
   - Must be the same across restarts

2. Check key in .env:
   ```bash
   grep CHRONOVAULT_MASTER_KEY .env
   ```

3. If key is lost, encrypted credentials cannot be recovered
   - Re-add servers with new SSH keys

### API Key Not Working

**Symptoms:**
```
Unauthorized: Invalid API key
```

**Solutions:**
1. Verify API key in Agent config matches Backend
2. Check if key is expired or revoked
3. Generate new API key in ChronoVault UI:
   - Settings → API Keys → Generate

### SSH Key Compromised

**Symptoms:**
- Unauthorized access detected
- Unusual activity on servers

**Solutions:**
1. Immediately rotate SSH keys:
   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519_new
   ```

2. Update all servers with new key:
   ```bash
   ssh-copy-id -i ~/.ssh/id_ed25519_new.pub user@server
   ```

3. Update ChronoVault server configuration

4. Check audit logs for suspicious activity:
   - Settings → Audit Logs

---

## Getting Help

### Collect Debug Information

```bash
# Backend logs
docker compose logs backend > backend.log

# Agent logs
sudo journalctl -u chronovault-agent > agent.log

# System info
uname -a > system-info.txt
df -h >> system-info.txt
free -h >> system-info.txt
```

### Report Issues

1. **GitHub Issues**: https://github.com/chronovault/chronovault/issues
2. **Include**:
   - ChronoVault version
   - OS and version
   - Steps to reproduce
   - Error messages
   - Logs (with sensitive data redacted)

### Community Support

- **Discussions**: https://github.com/chronovault/chronovault/discussions
- **Slack**: [Join our workspace](https://chronovault.slack.com)
