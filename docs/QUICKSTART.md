# ChronoVault 快速开始

## 5 分钟快速部署

### 前置要求

- Docker & Docker Compose v2+
- 4GB+ RAM
- 20GB+ 磁盘空间

### 1. 克隆项目

```bash
git clone https://github.com/your-org/chronovault.git
cd chronovault
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，设置以下必需变量：
# JWT_SECRET=$(openssl rand -hex 32)
# CHRONOVAULT_MASTER_KEY=$(openssl rand -hex 32)
# CHRONOVAULT_RESTIC_PASSWORD=$(openssl rand -hex 32)
```

### 3. 启动服务

```bash
docker-compose up -d
```

等待所有服务启动（约 30 秒），然后访问：

- **前端**: http://localhost
- **后端 API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html

### 4. 首次登录

打开浏览器访问 http://localhost，使用默认账户登录：

| 邮箱 | 密码 | 角色 |
|------|------|------|
| xuan@chronovault.io | password123 | OWNER |
| liwei@chronovault.io | password123 | ADMIN |
| zhangmin@chronovault.io | password123 | MEMBER |

> ⚠️ 生产环境请立即修改默认密码！

### 5. 添加第一台服务器

1. 登录后点击「服务器」→「添加服务器」
2. 填写服务器信息：
   - 名称：我的服务器
   - IP 地址：192.168.1.100
   - 操作系统：Ubuntu 22.04
3. 配置 SSH 连接：
   - 端口：22
   - 用户名：root
   - 认证方式：KEY（推荐）或 PASSWORD
   - 粘贴 SSH 私钥或输入密码
4. 点击「测试连接」验证
5. 点击「保存」完成添加

### 6. 创建第一个快照

1. 在服务器详情页点击「创建快照」
2. 选择存储目标（首次使用会自动创建本地存储）
3. 点击「开始备份」
4. 等待备份完成（进度条实时显示）

### 7. 查看系统状态

快照完成后，你可以：

- **查看 state.json**: 快照详情页显示采集的系统状态（包、服务、端口、Docker）
- **查看 Diff**: 选择两个快照对比变更
- **查看时间线**: Git 风格的快照历史

## 生产环境部署

### 使用外部数据库

```yaml
# docker-compose.prod.yml
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: chronovault
      POSTGRES_USER: chronovault
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    # 不映射端口，仅内部网络访问
```

### 使用 S3 存储

在「存储管理」中添加 S3 存储目标：

- 类型：S3
- 端点：https://s3.amazonaws.com
- Bucket：chronovault-backups
- Access Key：AKIA...
- Secret Key：...

### 配置 HTTPS

使用 Nginx 反向代理：

```nginx
server {
    listen 443 ssl;
    server_name chronovault.example.com;

    ssl_certificate /etc/ssl/certs/chronovault.pem;
    ssl_certificate_key /etc/ssl/private/chronovault.key;

    location / {
        proxy_pass http://localhost:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 常见问题

### SSH 连接失败

1. 确认服务器 IP 和端口正确
2. 确认 SSH 服务运行中：`systemctl status sshd`
3. 确认密钥格式正确（OpenSSH 格式）
4. 检查防火墙是否允许 SSH 端口

### 快照创建失败

1. 确认目标服务器已安装 restic（Agent 会自动安装）
2. 检查磁盘空间：`df -h`
3. 检查存储目标配置是否正确
4. 查看后端日志：`docker-compose logs backend`

### Agent 安装失败

1. 确认后端服务正常运行
2. 确认 Agent 可达后端 API
3. 检查 API Key 是否正确
4. 查看 Agent 日志：`journalctl -u chronovault-agent -f`