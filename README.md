# ChronoVault

> 服务器的"时间机器" — 智能备份恢复平台

[![CI](https://github.com/chronovault/chronovault/actions/workflows/ci.yml/badge.svg)](https://github.com/chronovault/chronovault/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-ED8B00.svg)](https://openjdk.org/projects/jdk/17/)
[![Go](https://img.shields.io/badge/Go-1.22-00ADD8.svg)](https://go.dev/)
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D.svg)](https://vuejs.org/)

ChronoVault 是一个企业级服务器备份与恢复平台，提供快照管理、时间旅行回滚、AI 驱动的风险分析和存储健康监控。

## 特性

- **快照管理** — 通过 Restic 创建加密增量备份，支持多存储后端（本地/S3/OSS/WebDAV）
- **时间旅行** — 一键回滚到任意历史快照，自动处理网络路由与持久化卷映射
- **AI 洞察** — 基于 MiMo 模型的智能分析，提供风险评分、异常检测和优化建议
- **存储健康** — 实时监控存储容量、延迟和吞吐量，预测增长趋势
- **告警中心** — 多渠道通知（Slack/DingTalk/Webhook/Email），AI 根因分析
- **跨服务器迁移** — 将快照作为镜像部署到新计算节点
- **定时备份** — Cron 表达式驱动的自动备份调度
- **团队协作** — 基于角色的访问控制（OWNER/ADMIN/MEMBER/VIEWER）

## 架构

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Frontend   │────▶│   Backend    │────▶│   Agent     │
│  Vue 3 + TS  │     │ Spring Boot  │     │  Go CLI     │
│  Vite 8      │     │ Java 17      │     │  Daemon     │
└─────────────┘     └──────┬───────┘     └──────┬──────┘
                           │                     │
                    ┌──────┴───────┐              │
                    │              │              │
              ┌─────▼─────┐ ┌─────▼─────┐  ┌────▼────┐
              │ PostgreSQL │ │   Redis   │  │  Restic │
              │    15      │ │    7      │  │  CLI    │
              └───────────┘ └───────────┘  └─────────┘
```

| 组件 | 技术栈 | 职责 |
|------|--------|------|
| **Backend** | Spring Boot 3.2.5 / Java 17 | REST API、SSH 连接池、快照引擎、AI 分析 |
| **Frontend** | Vue 3.5 / TypeScript / Vite 8 / Tailwind CSS 4 | SPA 界面、WebSocket 实时更新 |
| **Agent** | Go 1.22 | 目标服务器守护进程、Restic CLI 调用、环境扫描 |

## 快速开始

### 前置要求

- Docker & Docker Compose
- 或分别安装：Java 17、Node.js 20、Go 1.22、PostgreSQL 15、Redis 7

### Docker Compose 一键启动

```bash
# 克隆项目
git clone https://github.com/chronovault/chronovault.git
cd chronovault

# 配置环境变量
cp .env.example .env
# 编辑 .env，设置 JWT_SECRET、CHRONOVAULT_MASTER_KEY、CHRONOVAULT_RESTIC_PASSWORD

# 启动所有服务
docker-compose up -d

# 访问
# 前端: http://localhost
# 后端 API: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### 本地开发

```bash
# 启动基础设施
docker-compose up -d postgres redis

# 后端
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 前端
cd frontend
npm install
npm run dev    # http://localhost:5173

# Agent（在目标服务器上）
cd agent
go build -o chronovault-agent .
./chronovault-agent scan   # 扫描环境
./chronovault-agent run    # 启动守护进程
```

### 默认账户

| 邮箱 | 密码 | 角色 |
|------|------|------|
| xuan@chronovault.io | password123 | OWNER |
| liwei@chronovault.io | password123 | ADMIN |
| zhangmin@chronovault.io | password123 | MEMBER |

> 生产环境请立即修改默认密码并设置 `.env` 中的密钥。

## 环境变量

| 变量 | 必填 | 说明 | 生成方式 |
|------|------|------|---------|
| `JWT_SECRET` | 是 | JWT 签名密钥（≥32 字符） | `openssl rand -hex 32` |
| `CHRONOVAULT_MASTER_KEY` | 是 | AES-256-GCM 凭据加密密钥 | `openssl rand -hex 32` |
| `CHRONOVAULT_RESTIC_PASSWORD` | 是 | Restic 备份加密密码 | `openssl rand -hex 32` |
| `POSTGRES_*` | 否 | 数据库连接（默认 localhost:5432） | — |
| `REDIS_*` | 否 | Redis 连接（默认 localhost:6379） | — |
| `MIMO_API_KEY` | 否 | MiMo AI API 密钥（空则禁用 AI） | — |
| `CORS_ALLOWED_ORIGINS` | 生产 | 允许的前端来源 | — |

## API 文档

启动后端后访问 Swagger UI：

```
http://localhost:8080/swagger-ui.html
```

主要 API 端点：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/register` | 用户注册 |
| GET | `/api/servers` | 获取服务器列表 |
| POST | `/api/snapshots` | 创建快照 |
| POST | `/api/snapshots/{id}/rollback` | 回滚到快照 |
| GET | `/api/storage/overview` | 存储概览 |
| GET | `/api/ai/insights` | AI 洞察 |
| GET | `/api/alerts` | 告警列表 |
| POST | `/api/scheduled-backups` | 创建定时备份 |
| GET | `/api/risk/score` | 风险评分 |

## 项目结构

```
chronovault/
├── backend/                # Spring Boot 后端
│   ├── src/main/java/com/chronovault/
│   │   ├── controller/     # REST 控制器（17 个）
│   │   ├── service/        # 业务服务（15 个）
│   │   ├── entity/         # JPA 实体
│   │   ├── repository/     # Spring Data 仓库
│   │   ├── dto/            # 数据传输对象
│   │   ├── ssh/            # SSH 连接管理（MINA SSHD）
│   │   ├── snapshot/       # 快照引擎（Restic CLI）
│   │   ├── storage/        # 多存储后端（S3/OSS/WebDAV/LOCAL）
│   │   ├── ai/             # AI 分析（MiMo 模型）
│   │   └── security/       # JWT + AES-256-GCM 加密
│   └── src/main/resources/
│       └── db/migration/   # Flyway 迁移（V1-V22）
├── frontend/               # Vue 3 前端
│   └── src/
│       ├── api/            # Axios API 模块（14 个）
│       ├── views/          # 页面视图（14 个）
│       ├── components/     # 组件（含 11 个模态框）
│       ├── stores/         # Pinia 状态管理
│       ├── types/          # TypeScript 类型定义（13 个）
│       └── composables/    # WebSocket 等组合函数
├── agent/                  # Go Agent
│   ├── cmd/                # CLI 命令
│   ├── server/             # HTTP API 服务器
│   ├── scanner/            # 环境扫描（Docker/DB/Web/System）
│   ├── restic/             # Restic CLI 封装
│   ├── transport/          # 与后端通信的 HTTP 客户端
│   └── config/             # 配置管理
├── docker-compose.yml      # Docker Compose 编排
└── ROADMAP.md              # 成熟化路线图
```

## 安全模型

- **凭据加密** — SSH 密钥/密码使用 AES-256-GCM 加密存储
- **JWT 认证** — 无状态令牌认证，支持角色授权
- **SSH TOFU** — 默认信任首次连接，生产环境可配置 known_hosts
- **终端安全** — 危险命令拦截（rm -rf /、mkfs 等）
- **限流** — 认证端点 IP+路径 滑动窗口限流（30次/分钟）
- **Agent 认证** — Bearer token 中间件保护 Agent API

## 贡献

请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解开发环境搭建、代码规范和 PR 流程。

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可。
