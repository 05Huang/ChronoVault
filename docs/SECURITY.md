# ChronoVault 安全架构

## 概述

ChronoVault 采用多层安全机制保护服务器状态数据和备份资产。

## 认证与授权

### JWT 认证
- 使用 HS256 算法签名 JWT Token
- Secret Key 最少 32 字符（256 位），启动时校验
- Access Token 默认过期时间可配置
- Refresh Token 有效期 7 天
- 支持 Token 刷新，旧 Token 自动失效

### RBAC 角色模型
| 角色 | 权限 |
|------|------|
| OWNER | 完全管理权限，包括用户管理和系统配置 |
| ADMIN | 管理服务器、快照、存储，不能管理用户 |
| MEMBER | 创建快照、查看状态，不能修改系统配置 |
| VIEWER | 只读访问 |

### API Key 认证
- 支持 API Key 认证（适用于 Agent 和自动化脚本）
- API Key 使用 SHA-256 哈希存储
- 支持设置过期时间

## 数据加密

### 凭据加密 (AES-256-GCM)
- SSH 密钥和密码使用 AES-256-GCM 加密存储
- Master Key 通过 `CHRONOVAULT_MASTER_KEY` 环境变量配置
- 每次加密使用随机 IV，确保相同明文产生不同密文
- Master Key 最少 32 字符，启动时校验

### Restic 备份加密
- Restic 使用独立密码加密备份数据
- 通过 `CHRONOVAULT_RESTIC_PASSWORD` 配置
- 备份数据在传输和存储时均加密

## SSH 安全

### 连接安全
- 支持 Known Hosts 验证（生产环境推荐）
- TOFU (Trust On First Use) 模式作为开发环境备选
- 连接池限制最大连接数，防止 SSH 限流
- 空闲连接自动清理（默认 5 分钟）

### 密钥管理
- SSH 私钥加密存储在数据库中
- 临时密钥文件使用 0600 权限
- 密钥加载后立即删除临时文件
- 支持密钥轮换

## 网络安全

### CORS 配置
- 生产环境通过 `CORS_ALLOWED_ORIGINS` 限制允许的来源
- 开发环境默认允许 localhost

### WebSocket 安全
- WebSocket 连接需要认证（除公开事件外）
- `/ws/events` 和 `/ws/topics/**` 允许匿名访问
- 其他 WebSocket 路径需要有效 Token

### Rate Limiting
- 登录接口限制每分钟 30 次请求
- 使用 Redis 实现分布式限流

## 审计日志

### 记录内容
- 所有写操作自动记录审计日志
- 包含：操作类型、资源类型、资源 ID、操作者、IP 地址、时间戳
- 支持按资源类型和 ID 查询操作历史

### 保留策略
- 审计日志默认保留 90 天
- 支持配置自动清理

## 依赖安全

### 已知安全措施
- Spring Boot 定期更新以修复 CVE
- 所有依赖通过 Maven/npm 仓库拉取
- Docker 镜像使用官方基础镜像

### 建议
- 定期运行 `mvn dependency-check:check` 检查依赖漏洞
- 定期运行 `npm audit` 检查前端依赖
- 保持 Docker 基础镜像更新

## 部署安全

### 环境变量
- 所有敏感配置通过环境变量注入
- `.env` 文件已加入 `.gitignore`
- 不在日志中输出敏感信息

### Docker 安全
- 容器以非 root 用户运行（如适用）
- 资源限制防止 DoS
- 不映射不必要的端口到宿主机

### 数据库安全
- PostgreSQL 使用独立用户和密码
- 生产环境不映射数据库端口到宿主机
- 定期备份数据库