# ChronoVault 成熟化路线图

> 从功能原型到可发布开源产品的演进计划
> 创建日期：2026-05-26

---

## 阶段一：修复基础（预计 1-2 周）

> 目标：让端到端链路真正跑通，消除已知 bug

### 1.1 Agent 端核心逻辑补全

- [x] `agent/cmd/root.go` — 实现 `executeTask()` 中的 SNAPSHOT/RECOVER 逻辑，调用 restic CLI（参照后端 `ResticClient.java` 用 Go 重写）
- [x] `agent/server/api.go` — 实现 `/snapshot` 和 `/restore` 的实际执行逻辑
- [x] `agent/scanner/database.go` — 修复 `itoa()` bug，改用 `strconv.Itoa()`
- [x] `agent/main.go` — 删除未使用的 `waitForSignal()` 死代码
- [x] 新增 `agent/restic/client.go` — 封装 restic CLI 调用（init、backup、restore、snapshots、diff）

### 1.2 前端 TypeScript 类型补全

- [x] 新增 `frontend/src/types/` 目录，为所有 API 响应定义接口（12 个类型文件）
- [x] 更新所有 `views/` 和 `api/` 文件，将 `any` 替换为具体类型
- [x] 修复 Axios 拦截器自动解包 ApiResponse wrapper
- [x] 删除 `NewBackupModal.vue` 的 `console.log` 调试残留

### 1.3 Agent 端补全测试

- [x] 新增 `agent/restic/client_test.go` — 测试命令构建和 shell 转义
- [x] 新增 `agent/scanner/database_test.go` — 测试端口扫描和版本提取
- [x] 新增 `agent/transport/client_test.go` — 测试 HTTP 客户端（httptest 模拟服务端）

---

## 阶段二：生产基础设施（预计 1-2 周）

> 目标：可部署、可监控、可自动化

### 2.1 容器化

- [x] 新增 `backend/Dockerfile` — 多阶段构建：Maven 编译 + JRE 运行镜像
- [x] 新增 `agent/Dockerfile` — Go 多阶段构建，Alpine 最终镜像
- [x] 新增 `frontend/Dockerfile` — Node 构建 + Nginx 静态托管
- [x] 新增根目录 `docker-compose.yml` — 编排 PostgreSQL + Redis + Backend + Frontend（含 Nginx 反向代理）

### 2.2 CI/CD

- [x] 新增 `.github/workflows/ci.yml` — GitHub Actions：
  - Backend: `mvn compile` + `mvn test`
  - Frontend: `npx vue-tsc --noEmit` + `npm run build`
  - Agent: `go build` + `go test`
- [x] 新增 `.github/workflows/release.yml` — 标签触发的自动构建 + Docker 镜像推送

### 2.3 后端生产加固

- [x] `DashboardService.java` — `getActivityTrend` 改为日期范围查询，不再 `findAll()` 全量加载
- [x] 新增 `RateLimitFilter.java` — 基于 IP+路径 滑动窗口限流（30次/分钟，保护 /api/auth/*）
- [x] `application-prod.yml` — actuator metrics/info 需要认证、健康检查公开（已确认正确）
- [x] `LocalStorageProvider.java` — `getHealth` 实际测量写入延迟和吞吐量

### 2.4 Agent 安全加固

- [x] `agent/server/api.go` — 添加 Bearer token 认证中间件（/health 公开）
- [x] `agent/config/config.go` — 支持 TLS 配置（tls_enabled/tls_cert/tls_key/auth_token）

---

## 阶段三：功能完善（预计 2-3 周）

> 目标：补齐产品级功能

### 3.1 定时备份

- [x] 后端 — 新增 `ScheduledBackup` 实体 + Flyway 迁移 (V22)
- [x] 后端 — `ScheduledBackupService` 使用 `@Scheduled` 每分钟检查到期任务
- [x] 前端 — 新增 API 模块 `scheduledBackups.ts` + 类型定义
- [x] API — `POST /api/scheduled-backups`、`GET`、`DELETE`、`PUT`（启用/禁用）

### 3.2 审计日志查询

- [x] 后端 — `SettingsController` 新增 `/audit-logs/search` 分页查询（支持 action/userId/since/until）
- [x] 前端 — `Settings.vue` 添加筛选表单（操作类型、时间范围）+ 搜索/重置按钮

### 3.3 通知渠道完善

- [x] 后端 — `AlertService` 新增 `notifyAlert()` 方法，支持 Slack/DingTalk/Webhook/Email 四种渠道
- [x] 后端 — 使用 `java.net.http.HttpClient` 异步发送，带超时和错误处理

### 3.4 批量操作

- [x] 后端 — `SnapshotController` 新增 `POST /api/snapshots/batch-delete`
- [x] 后端 — `ServerController` 新增 `POST /api/servers/batch-scan`
- [x] 后端 — `SnapshotService.batchDelete()` + `ServerService.batchScan()`

### 3.5 数据导出

- [x] 后端 — 新增 `GET /api/snapshots/export?format=csv|json`（CSV/JSON）
- [x] 后端 — 自动设置 Content-Disposition 下载头

---

## 阶段四：开源规范（预计 1 周）

> 目标：让外部贡献者能参与

### 4.1 文档

- [x] 更新 `README.md` — 项目介绍、架构图、快速开始（Docker Compose + 本地开发）、API 概览、安全模型
- [x] 新增 `CONTRIBUTING.md` — 开发环境搭建、代码规范（Java/TypeScript/Go）、Conventional Commits、PR 流程
- [x] 新增 `CHANGELOG.md` — 基于 Keep a Changelog 格式，涵盖三个阶段的变更记录

### 4.2 版本管理

- [x] 语义化版本 — `0.1.0-SNAPSHOT` → `0.1.0`
- [x] 集成 springdoc-openapi — 添加依赖 + SecurityConfig 白名单 + OpenAPI 元数据配置
- [x] Git 标签策略 — `v0.1.0`、`v0.2.0` 等

### 4.3 开源合规

- [x] 新增 `LICENSE` — Apache License 2.0
- [x] 审查 `.gitignore` — 修复 `storage/` 和 `*.sql` 过于宽泛的模式，确保源码和迁移不被忽略
- [x] 新增 `SECURITY.md` — 安全漏洞报告流程、部署/运行时安全最佳实践

---

## 阶段五：高级优化（持续迭代）

> 目标：性能、可靠性、扩展性

### 5.1 性能优化

- [ ] 后端数据库查询优化（索引审计、N+1 检测）
- [x] 前端路由懒加载 — 所有视图已使用 `() => import(...)` 动态导入
- [ ] 快照列表虚拟滚动（大数据量场景）
- [ ] Redis 缓存热点数据（Dashboard 统计、服务器列表）

### 5.2 可靠性

- [ ] SSH 连接健康检查改进
- [ ] 快照任务断点续传（记录已备份路径）
- [ ] Agent 端离线重连 + 任务队列持久化
- [x] 后端优雅关闭 — `AsyncTaskManager.@PreDestroy` 等待运行中任务完成（60s 超时）

### 5.3 扩展性

- [ ] 多存储后端并行写入（镜像备份）
- [x] 快照保留策略自动清理 — `SnapshotRetentionPolicy` 实体 + 每日 3:00 定时清理（按数量/天数/保护期）
- [ ] Webhook 事件推送（备份完成、告警触发等）
- [ ] 插件化存储后端（SPI 机制）

---

## 实施优先级

| 顺序 | 阶段 | 关键产出 | 风险 |
|------|------|---------|------|
| 1 | 基础修复 | Agent 可执行备份 + 前端类型安全 | Agent restic 调用需实机测试 |
| 2 | 生产基建 | `docker-compose up` 一键启动 | Nginx + WebSocket 代理需调试 |
| 3 | 功能完善 | 定时备份 + 审计日志 + 通知 | 定时任务的时区和并发控制 |
| 4 | 开源规范 | 可发布的 `v0.1.0` | 文档维护成本 |
| 5 | 高级优化 | 按需迭代 | 性能测试需要真实环境 |

**建议从阶段一开始**——Agent 端空壳是当前最大的功能性缺陷，用户安装 Agent 却发现不能真正备份，会直接丧失信任。
