<div align="center">
<img src="logo.webp" alt="ChronoVault Logo" width="400">


[![Tests](https://img.shields.io/badge/tests-697%20passing-brightgreen)](#testing)
[![Coverage](https://img.shields.io/badge/coverage-85%25-brightgreen)](#testing)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.1.0--alpha-orange)](CHANGELOG.md)

[English](README_EN.md) | 中文文档

</div>

---

## 什么是 ChronoVault？

ChronoVault 是一款受 Git 启发（Git-inspired）的服务器状态版本控制平台。传统备份工具只备份文件，而 ChronoVault 备份的是**整个系统状态**——包括已安装的包、运行的服务、监听的端口、Docker 容器、配置文件哈希、定时任务等。该项目由 Java 17 + Spring Boot 3.2.5  构建后端、Vue 3.5 + TypeScript 构建前端、Go 1.22 构建轻量级 Agent，采用 PostgreSQL 15 + Redis 7 存储，通过 Restic CLI  执行备份引擎，实现了真正的状态感知快照（不仅备份文件，还收集 packages、services、ports、Docker 容器、配置文件哈希、crontab 等系统状态）。核心功能包括 Git 风格的  Snapshot/Branch/Diff/Revert/Bisect/Clone/Cherry-pick/Stash/Blame 操作、智能状态对比引擎（清晰展示包升级、服务重启、端口变化等）、选择性回滚（可单独回滚特定配置文件或包而非全量恢  复）、多服务器集中管理、定时备份与保留策略、多存储后端支持（Local/S3/OSS/WebDAV）、Webhook 通知（Slack/DingTalk）、AI 驱动分析、RBAC 权限控制、审计日志和 WebSocket  实时更新。

**就像 Git 管理代码一样，ChronoVault 管理服务器状态。**

## 为什么选择 ChronoVault？

<table>
<tr>
<td width="50%">

### 🔥 传统备份工具的痛点

- 只能备份文件，无法感知系统状态变化
- 恢复时只能全量还原，无法选择性回滚
- 无法追踪"是谁、在什么时候、改了什么"
- 缺乏 Git 风格的分支管理和版本对比
- 多服务器管理复杂，缺乏统一视图

### ✨ ChronoVault 的解决方案

- **状态感知快照**：捕获完整的系统状态，不只是文件
- **智能差异对比**：精确显示包升级、服务重启、端口变化
- **选择性回滚**：单独回滚某个配置文件或包，无需全量恢复
- **Git-like 操作**：Branch、Diff、Bisect、Cherry-pick，开发者熟悉的工作流
- **集中化管理**：一个仪表板管理所有服务器

## 功能对比

| 能力 | ChronoVault | 传统备份工具 (Restic) | 配置管理工具 (Ansible) |
|------|-------------|----------------------|----------------------|
| **状态收集** | ✅ 包、服务、端口、Docker、配置、Crontab | ❌ 仅文件 | ⚠️ 需手动定义 |
| **状态对比** | ✅ 可视化差异（包/服务/端口/配置） | ❌ 仅文件 diff | ❌ 不支持 |
| **选择性回滚** | ✅ 单个包或配置文件级别 | ❌ 仅全量恢复 | ⚠️ 需手动操作 |
| **Git-like 操作** | ✅ Branch、Bisect、Cherry-pick、Stash、Blame | ❌ 不支持 | ❌ 不支持 |
| **变更追踪** | ✅ 谁改了什么、什么时候改的 | ❌ 不支持 | ⚠️ 仅 playbook 日志 |
| **多服务器** | ✅ 集中化仪表板 | ❌ 单服务器 | ✅ 支持 |
| **告警通知** | ✅ 高风险变更自动检测 | ❌ 不支持 | ❌ 不支持 |

## 功能亮点

### 🎯 核心功能

| 操作 | Git 对应 | 说明 |
|------|---------|------|
| **Snapshot** | `git commit` | 创建全量/增量快照，捕获完整系统状态 |
| **Rollback** | `git checkout` | 恢复到任意历史快照 |
| **Revert** | `git revert` | 撤销特定快照的更改 |
| **Branch** | `git branch` | 为服务器创建并行状态轨道（生产/预发/开发） |
| **Bisect** | `git bisect` | 二分查找哪个快照引入了问题 |
| **Clone** | `git clone` | 复制服务器配置到新环境 |
| **Cherry-pick** | `git cherry-pick` | 将特定更改应用到目标服务器 |
| **Stash** | `git stash` | 临时保存当前状态 |
| **Blame** | `git blame` | 查看谁在什么时候修改了什么 |

### 🏗️ 基础设施功能

- **Drift Detection** — 监控容器、文件、端口变化
- **Auto-snapshot** — 检测到漂移时自动创建快照
- **Scheduled Backups** — 基于 Cron 的自动化备份
- **Webhooks** — Slack、钉钉、自定义 URL 集成
- **Verification Jobs** — 定期备份完整性检查
- **Disaster Recovery** — 基于 Runbook 的恢复计划

### 🔒 安全特性

- **AES-256-GCM 加密** — 凭据静态加密存储
- **JWT 认证 + RBAC** — OWNER / ADMIN / MEMBER / VIEWER 角色
- **审计日志** — 所有操作可追溯
- **限流保护** — 防止暴力破解和滥用
- **XSS 防护** — 输入过滤与转义

---

## 快速开始

### 前置条件

- Docker & Docker Compose
- Git

### 一键启动

```bash
# 克隆仓库
git clone https://github.com/chronovault/chronovault.git
cd chronovault

# 启动所有服务
docker-compose up -d

# 访问应用
open http://localhost
```

### 默认账户

| 字段 | 值 |
|------|---|
| 邮箱 | `admin@chronovault.com` |
| 密码 | `admin123` |

> **安全提示**：请在首次登录后立即修改默认密码。

### 本地开发

```bash
# 1. 启动基础设施
docker-compose up -d postgres redis

# 2. 启动后端
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 启动前端
cd frontend
npm install
npm run dev
```

---

## 架构概览

```
┌──────────────────────────────────────────────────────────────┐
│                        Frontend                              │
│              Vue 3.5 + TypeScript + Vite + Pinia             │
└──────────────────────────┬───────────────────────────────────┘
                           │ REST API + WebSocket
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                        Backend                               │
│             Java 17 + Spring Boot 3.2.5 + JPA                │
│                                                              │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│   │   Snapshot   │  │    State     │  │     SSH      │      │
│   │   Engine     │  │  Diff Engine │  │  Connection  │      │
│   └──────────────┘  └──────────────┘  └──────────────┘      │
└──────────────────────────┬───────────────────────────────────┘
                           │
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
      ┌──────────┐  ┌──────────┐  ┌──────────┐
      │PostgreSQL│  │  Redis   │  │  Restic  │
      │   15     │  │    7     │  │   CLI    │
      └──────────┘  └──────────┘  └──────────┘

                           │ SSH
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                         Agent                                │
│                  Go 1.22 + Restic CLI                        │
│                                                              │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│   │   Scanner    │  │    State     │  │  Local API   │      │
│   │ (state.json) │  │   Collector  │  │    Server    │      │
│   └──────────────┘  └──────────────┘  └──────────────┘      │
└──────────────────────────────────────────────────────────────┘
                           │
                           ▼
                     Target Server
```

### 技术栈

| 组件 | 技术 |
|------|------|
| **Frontend** | Vue 3.5, TypeScript 5.3, Vite 5, Tailwind CSS, Pinia |
| **Backend** | Java 17, Spring Boot 3.2.5, Spring Security, JPA, Flyway |
| **Database** | PostgreSQL 15, Redis 7 |
| **Agent** | Go 1.22, Restic CLI |
| **Infrastructure** | Docker, Docker Compose, Nginx |
| **Monitoring** | Prometheus, Grafana, Micrometer |

---

## 项目结构

```
chronovault/
├── backend/                      # Spring Boot REST API
│   ├── src/main/java/com/chronovault/
│   │   ├── controller/           # REST 控制器
│   │   ├── service/              # 业务逻辑
│   │   ├── repository/           # 数据访问层
│   │   ├── entity/               # JPA 实体
│   │   ├── dto/                  # 数据传输对象
│   │   ├── diff/                 # 状态差异引擎
│   │   ├── snapshot/             # 快照引擎
│   │   ├── ssh/                  # SSH 连接管理
│   │   └── websocket/            # WebSocket 处理
│   └── src/main/resources/db/migration/  # Flyway 迁移 (42个版本)
│
├── frontend/                     # Vue 3 SPA
│   └── src/
│       ├── views/                # 页面视图
│       ├── components/           # Vue 组件
│       ├── api/                  # API 客户端
│       ├── stores/               # Pinia 状态管理
│       └── types/                # TypeScript 类型定义
│
├── agent/                        # Go CLI 守护进程
│   ├── cmd/                      # CLI 命令
│   ├── scanner/                  # 环境扫描器（state.json 收集）
│   ├── restic/                   # Restic CLI 封装
│   └── transport/                # HTTP 客户端
│
├── terraform/                    # Terraform Provider 示例
├── monitoring/                   # Prometheus + Grafana 配置
└── docs/                         # 项目文档
```

---

## API 文档

启动后端后访问 Swagger UI：

```
http://localhost:8080/swagger-ui.html
```

### 快速示例

```bash
# 创建快照
curl -X POST http://localhost:8080/api/snapshots \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"serverId": 1, "paths": ["/etc", "/var/www"]}'

# 获取快照差异
curl http://localhost:8080/api/snapshots/1/diff?targetId=2 \
  -H "Authorization: Bearer <token>"

# 回滚到指定快照
curl -X POST http://localhost:8080/api/snapshots/1/rollback \
  -H "Authorization: Bearer <token>"
```

完整 API 文档请参考 [API_REFERENCE.md](docs/API_REFERENCE.md)

---

## 部署

### Docker Compose（推荐）

```bash
# 生产环境部署
docker-compose -f docker-compose.prod.yml up -d
```

### Agent 安装

```bash
# 下载并安装 Agent
wget https://github.com/chronovault/chronovault/releases/latest/download/chronovault-agent-linux-amd64
chmod +x chronovault-agent-linux-amd64

# 注册到后端
./chronovault-agent-linux-amd64 register \
  --server-url http://your-backend:8080 \
  --api-key YOUR_API_KEY
```

详细部署说明请参考 [DEPLOYMENT.md](docs/DEPLOYMENT.md) 和 [AGENT_INSTALLATION.md](docs/AGENT_INSTALLATION.md)

---

## 文档

| 文档 | 说明 |
|------|------|
| [快速开始](docs/QUICKSTART.md) | 5 分钟快速上手 |
| [架构设计](docs/ARCHITECTURE.md) | 系统架构详解 |
| [API 参考](docs/API_REFERENCE.md) | 完整 API 文档 |
| [用户指南](docs/USER_GUIDE.md) | 功能使用说明 |
| [Agent 安装](docs/AGENT_INSTALLATION.md) | Agent 部署指南 |
| [部署指南](docs/DEPLOYMENT.md) | 生产环境部署 |
| [故障排除](docs/TROUBLESHOOTING.md) | 常见问题解决 |
| [贡献指南](CONTRIBUTING.md) | 如何参与贡献 |
| [安全政策](SECURITY.md) | 安全漏洞报告 |

---

## 测试

### 运行测试

```bash
# 后端测试
cd backend && mvn test

# 前端类型检查
cd frontend && npx vue-tsc --noEmit

# Agent 测试
cd agent && go test -race ./...
```

### 测试覆盖

| 组件 | 源文件 | 测试文件 | 说明 |
|------|--------|---------|------|
| Backend | 295 | 84 | Controller、Service、Repository、WebSocket |
| Frontend | 106 | TypeScript 类型检查 + E2E | vue-tsc + Postman 集合 |
| Agent | 14 | 4 | restic、scanner、transport |

---

## 贡献

我们欢迎所有形式的贡献！请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解如何参与。

### 开发流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

### 提交规范

我们使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

| 前缀 | 说明 |
|------|------|
| `feat:` | 新功能 |
| `fix:` | Bug 修复 |
| `docs:` | 文档更新 |
| `style:` | 代码格式（不影响功能） |
| `refactor:` | 代码重构 |
| `test:` | 测试相关 |
| `chore:` | 构建/工具链变更 |
| `perf:` | 性能优化 |

---

## 社区与支持

- **GitHub Issues** — [提交问题或建议](https://github.com/chronovault/chronovault/issues)
- **Discussions** — [参与讨论](https://github.com/chronovault/chronovault/discussions)
- **Security** — [报告安全漏洞](SECURITY.md)

---

## 许可证

本项目采用 [MIT 许可证](LICENSE) 开源。

---

<div align="center">

**如果 ChronoVault 对你有帮助，请给我们一个 ⭐ Star 支持！**

</div>