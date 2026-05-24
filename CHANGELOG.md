# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

#### Phase 4 — Open Source Standards
- `README.md` — 完整的项目文档，包含架构图、快速开始、API 概览、安全模型
- `CONTRIBUTING.md` — 开发环境搭建、代码规范、提交规范、PR 流程
- `LICENSE` — Apache License 2.0
- `SECURITY.md` — 安全漏洞报告流程与部署安全最佳实践
- `CHANGELOG.md` — 变更日志（本文件）
- Swagger/OpenAPI 文档集成（springdoc-openapi）

## [0.1.0] — 2026-05-26

### Added

#### Phase 3 — Feature Completion
- **定时备份** — `ScheduledBackup` 实体与 CRUD API，基于 `@Scheduled` 的 Cron 表达式驱动自动执行
- **审计日志查询** — `GET /api/settings/audit-logs/search`，支持按操作类型、用户、时间范围筛选与分页
- **通知渠道完善** — AlertService 新增 Slack、钉钉、通用 Webhook、邮件四种通知渠道的实际发送逻辑
- **批量操作** — `POST /api/snapshots/batch-delete` 和 `POST /api/servers/batch-scan`
- **数据导出** — `GET /api/snapshots/export?format=csv|json`，支持 CSV 和 JSON 格式导出

#### Phase 2 — Production Infrastructure
- **Docker 多阶段构建** — backend（Maven + JRE）、agent（Go + Alpine）、frontend（Node + Nginx）
- **docker-compose.yml** — PostgreSQL 15 + Redis 7 + Backend + Frontend 一键编排
- **Nginx 反向代理** — Vue Router history 模式、/api/ 代理到 backend、/ws/ WebSocket 代理
- **GitHub Actions CI** — Backend（Maven test）、Frontend（vue-tsc + build）、Agent（go test）并行执行
- **GitHub Actions Release** — 标签触发的 Docker 镜像构建与 GHCR 推送
- **RateLimitFilter** — 认证端点 IP+路径 滑动窗口限流（30 次/分钟），返回 429 JSON
- **Agent Bearer Token 认证** — 中间件校验 Authorization header，/health 端点豁免
- **Agent TLS 支持** — 配置项 `tls_enabled`、`tls_cert`、`tls_key`
- **LocalStorageProvider 健康检测** — 实际写入 4KB 临时文件测量延迟与吞吐量

#### Phase 1 — Foundation Fixes
- **Agent Restic 集成** — 完整的 restic CLI 封装（init、backup、restore、snapshots、diff）
- **Agent 核心任务执行** — `executeTask()` 实现 SNAPSHOT/RECOVER 逻辑
- **Agent 测试** — `restic/client_test.go`、`scanner/database_test.go`、`transport/client_test.go`
- **前端 TypeScript 类型系统** — 13 个类型文件覆盖所有 API 响应
- **消除 `any` 类型** — Alerts、RiskCenter、Snapshots 等视图全面类型化

#### Core Features (Initial Release)
- **快照管理** — 通过 Restic 创建加密增量备份，支持本地/S3/OSS/WebDAV 存储后端
- **时间旅行回滚** — 一键回滚到任意历史快照，自动处理网络路由与持久化卷映射
- **AI 洞察** — 基于 MiMo 模型的智能分析，提供风险评分、异常检测和优化建议
- **存储健康监控** — 实时监控存储容量、延迟和吞吐量
- **告警中心** — 多渠道通知配置，事件聚合与管理
- **SSH 连接池** — Apache MINA SSHD 管理，支持密钥/密码认证
- **JWT 认证** — 无状态令牌认证，基于角色的访问控制（OWNER/ADMIN/MEMBER/VIEWER）
- **AES-256-GCM 加密** — SSH 凭据和存储凭据加密存储
- **WebSocket 实时更新** — STOMP/SockJS 推送任务进度与事件通知
- **Flyway 数据库迁移** — V1-V22 迁移脚本管理 PostgreSQL schema
- **Vue 3 前端** — 14 个页面视图、11 个模态框、Pinia 状态管理、Tailwind CSS 4

### Fixed
- `DashboardService.getActivityTrend` 改为日期范围查询，避免全量加载事件表
- `.gitignore` 中 `storage/` 和 `*.sql` 模式过于宽泛，导致 Java 源码和 Flyway 迁移被忽略
- Agent `database.go` 的 `itoa()` bug，改用 `strconv.Itoa()`

### Security
- 所有 API 端点（除认证端点）需要 JWT 认证
- Agent API 支持 Bearer token 认证
- SSH 凭据使用 AES-256-GCM 加密存储
- 危险终端命令自动拦截（rm -rf /、mkfs、dd 等）
- 认证端点 IP 限流保护

[Unreleased]: https://github.com/chronovault/chronovault/compare/v0.1.0-alpha...HEAD
[0.1.0]: https://github.com/chronovault/chronovault/releases/tag/v0.1.0
