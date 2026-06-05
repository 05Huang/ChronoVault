# ChronoVault Deployment Guide

> Deploy ChronoVault from zero to production on Linux (Ubuntu/Debian/CentOS)

---

## System Requirements

### Minimum Configuration

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 2 cores | 4 cores |
| Memory | 4 GB | 8 GB |
| Disk | 50 GB | 100 GB+ |
| Network | 10 Mbps | 100 Mbps |
| OS | Ubuntu 20.04+ / CentOS 7+ | Ubuntu 22.04 LTS |

### Software Dependencies

| Software | Version | Purpose |
|----------|---------|---------|
| Docker | 24.0+ | Container deployment |
| Docker Compose | 2.20+ | Service orchestration |
| Git | 2.30+ | Code management |

### Port Requirements

| Port | Service | Description |
|------|---------|-------------|
| 80 | Frontend (nginx) | HTTP access |
| 443 | Frontend (nginx) | HTTPS access (optional) |
| 8080 | Backend | API service |
| 5432 | PostgreSQL | Database (internal only) |
| 6379 | Redis | Cache (internal only) |
| 8081 | Agent | Agent service (per target server) |

---

## Quick Deploy (Docker Compose)

### 1. Install Docker

**Ubuntu/Debian:**
```bash
curl -fsSL https://get.docker.com | sh
sudo systemctl enable docker && sudo systemctl start docker
sudo usermod -aG docker $USER
newgrp docker
docker --version
docker compose version
```

**CentOS/RHEL:**
```bash
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable docker && sudo systemctl start docker
sudo usermod -aG docker $USER
newgrp docker
```

### 2. Clone and Configure

```bash
git clone https://github.com/chronovault/chronovault.git
cd chronovault
cp .env.example .env
```

### 3. Generate Security Keys

```bash
cat > .env << EOF
POSTGRES_DB=chronovault
POSTGRES_USER=chronovault
POSTGRES_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -hex 32)
CHRONOVAULT_MASTER_KEY=$(openssl rand -hex 32)
CHRONOVAULT_RESTIC_PASSWORD=$(openssl rand -hex 32)
SPRING_PROFILES_ACTIVE=prod
CORS_ALLOWED_ORIGINS=https://your-domain.com
EOF
echo "Warning: Save .env file securely!"
```

### 4. Start Services

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

### 5. Verify Deployment

```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP",...}
```

### 6. Access System

- Open browser to http://your-server-ip
- First visit redirects to registration page
- First registered user becomes OWNER automatically

---

## Manual Deploy

### Install Dependencies

**Ubuntu/Debian - Java 17:**
```bash
sudo apt update && sudo apt install -y openjdk-17-jdk
java -version
```

**Ubuntu/Debian - Node.js 20:**
```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
source ~/.bashrc
nvm install 20 && nvm use 20
node --version
```

**Ubuntu/Debian - PostgreSQL 15:**
```bash
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
sudo apt update && sudo apt install -y postgresql-15
sudo -u postgres psql -c "CREATE USER chronovault WITH PASSWORD 'your-password';"
sudo -u postgres psql -c "CREATE DATABASE chronovault OWNER chronovault;"
```

**Ubuntu/Debian - Redis 7:**
```bash
sudo apt install -y redis-server
sudo systemctl enable redis-server && sudo systemctl start redis-server
```

**Go 1.22 (for Agent):**
```bash
wget https://go.dev/dl/go1.22.0.linux-amd64.tar.gz
sudo tar -C /usr/local -xzf go1.22.0.linux-amd64.tar.gz
echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc
source ~/.bashrc
go version
```

### Deploy Backend

```bash
cd backend
export SPRING_PROFILES_ACTIVE=prod
export POSTGRES_HOST=localhost
export JWT_SECRET=your-jwt-secret
export CHRONOVAULT_MASTER_KEY=your-master-key
export CHRONOVAULT_RESTIC_PASSWORD=your-restic-password

./mvnw clean package -DskipTests
java -jar target/chronovault-backend-*.jar
```

**Systemd service:**
```bash
sudo tee /etc/systemd/system/chronovault-backend.service << 'EOF'
[Unit]
Description=ChronoVault Backend
After=network.target postgresql.service redis.service

[Service]
User=chronovault
WorkingDirectory=/opt/chronovault/backend
ExecStart=/usr/bin/java -jar target/chronovault-backend-*.jar
Restart=always
RestartSec=10
Environment=SPRING_PROFILES_ACTIVE=prod
EnvironmentFile=/opt/chronovault/.env

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable chronovault-backend
sudo systemctl start chronovault-backend
```

### Deploy Frontend

```bash
cd frontend
npm install && npm run build
sudo apt install -y nginx

sudo tee /etc/nginx/sites-available/chronovault << 'EOF'
server {
    listen 80;
    server_name your-domain.com;

    location / {
        root /opt/chronovault/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
EOF

sudo ln -s /etc/nginx/sites-available/chronovault /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

### Deploy Agent

Agent needs to be deployed to each managed server.

```bash
cd agent
go build -o chronovault-agent .

scp chronovault-agent user@target-server:/opt/chronovault/
ssh user@target-server

mkdir -p /etc/chronovault
cat > /etc/chronovault/agent.yaml << 'EOF'
server:
  backend_url: "http://your-chronovault-server:8080"
  api_key: "your-api-key"
  server_name: "web-server-01"

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
EOF

sudo tee /etc/systemd/system/chronovault-agent.service << 'EOF'
[Unit]
Description=ChronoVault Agent
After=network.target docker.service

[Service]
User=root
WorkingDirectory=/opt/chronovault
ExecStart=/opt/chronovault/chronovault-agent run
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable chronovault-agent
sudo systemctl start chronovault-agent
```

---

## Configuration

### Backend Configuration

Key environment variables:

| Variable | Required | Description |
|----------|----------|-------------|
| JWT_SECRET | Yes | JWT signing key (min 32 chars) |
| CHRONOVAULT_MASTER_KEY | Yes | AES-256-GCM encryption master key |
| CHRONOVAULT_RESTIC_PASSWORD | Yes | Restic backup encryption password |
| POSTGRES_HOST | No | Database host (default localhost) |
| POSTGRES_PORT | No | Database port (default 5432) |
| POSTGRES_DB | No | Database name (default chronovault) |
| POSTGRES_USER | No | Database user (default chronovault) |
| POSTGRES_PASSWORD | No | Database password (default chronovault) |
| REDIS_HOST | No | Redis host (default localhost) |
| REDIS_PORT | No | Redis port (default 6379) |
| SPRING_PROFILES_ACTIVE | No | Profile (default dev) |
| CORS_ALLOWED_ORIGINS | No | CORS allowed origins |
| MIMO_API_KEY | No | AI feature API key |

---

## SSL/TLS Configuration

Using Let's Encrypt:

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com

# Auto-renewal
sudo crontab -e
# Add: 0 12 * * * /usr/bin/certbot renew --quiet
```

---

## Monitoring and Logs

### Health Check

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

### View Logs

```bash
# Docker
docker compose logs -f backend

# Systemd
sudo journalctl -u chronovault-backend -f
```

---

## Database Backup

```bash
#!/bin/bash
BACKUP_DIR="/opt/chronovault/backups"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

pg_dump -U chronovault chronovault | gzip > $BACKUP_DIR/db_$DATE.sql.gz
find $BACKUP_DIR -name "db_*.sql.gz" -mtime +7 -delete
```

Scheduled backup:
```bash
sudo crontab -e
0 2 * * * /opt/chronovault/scripts/backup-db.sh >> /var/log/chronovault-backup.log 2>&1
```

---

## Upgrade Guide

**Docker deploy:**
```bash
cd /opt/chronovault
git pull origin main
docker compose up -d --build
docker compose ps
```

**Manual deploy:**
```bash
cd /opt/chronovault
git pull origin main

cd backend
./mvnw clean package -DskipTests
sudo systemctl restart chronovault-backend

cd ../frontend
npm install && npm run build
sudo systemctl reload nginx
```

---

## Troubleshooting

1. **Database connection failed**: Check PostgreSQL status, pg_hba.conf config
2. **Redis connection failed**: Check Redis status, redis-cli ping
3. **SSH connection failed**: Check firewall, SELinux, SSH key config
4. **Backup failed**: Check Restic installation, disk space, logs
5. **Port in use**: `sudo lsof -i :8080` to find occupying process
