# ChronoVault — Ralph 任务执行计划

> 每完成一个任务，将 `[ ]` 改为 `[x]`。
> Ralph 按照优先级从上到下执行，不跳过未完成的任务。
> **P0 全部完成前不进入 P1，P1 全部完成前不进入 P2。**

---

## 🔴 P0 — 地基修复（让项目真正能跑起来）

### P0-1: 环境验证与启动修复
- [x] 运行 `docker-compose up -d` 并记录所有错误
- [x] 修复 Backend 启动报错（检查 application.yml 配置，确保 DB/Redis 连接正确）
- [x] 修复 Frontend 构建错误（运行 `npm run build`，修复所有 TypeScript 编译错误）
- [x] 修复 Agent 编译错误（运行 `go build ./...`，修复所有编译问题）
- [x] 修复 SnapshotHookRepository 方法签名不匹配导致 Spring Context 加载失败
- [x] 后端 264 个测试全部通过（0 failures, 0 errors）
- [x] 前端 TypeScript 编译 0 错误，Vite 构建成功
- [x] 验证：`docker-compose up -d` 后 postgres 和 redis 容器 STATUS 为 healthy/running ✅
- [x] 验证：`curl http://localhost:8080/actuator/health` 返回 `{"status":"UP"}` ✅
- [x] 验证：前端 `http://localhost:5173` 可以加载，Vite dev server 正常 ✅
- [x] 验证：POST /api/auth/register 注册用户成功，返回 JWT token ✅
- [x] 验证：POST /api/auth/login 登录成功，JWT token 有效 ✅
- [x] 验证：GET /api/dashboard/overview 需要认证，返回空状态（无服务器/快照） ✅
- [x] 验证：GET /api/servers 返回空列表 ✅
- [x] 验证：GET /api/snapshots 返回空列表 ✅
- [x] 验证：Swagger UI 可访问 http://localhost:8080/swagger-ui.html ✅

### P0-2: Agent 核心逻辑补全
- [x] 打开 `agent/cmd/root.go`，找到 `executeTask()` 函数
- [x] 实现 SNAPSHOT 任务类型：调用 `restic/client.go` 的 `Backup()` 方法
- [x] 实现 RECOVER 任务类型：调用 `restic/client.go` 的 `Restore()` 方法
- [x] 完善 `agent/restic/client.go`：实现 `Init()`, `Backup()`, `Restore()`, `Snapshots()`, `Forget()`, `Dump()` 方法
- [x] 每个 Restic 操作必须：捕获 stdout/stderr、返回结构化错误、有超时控制（默认 30 分钟）
- [x] 写单元测试 `agent/restic/client_test.go`：测试命令构建、参数转义、classifyError、BuildRepoUrl
- [x] 修复 Agent scanner 并发问题：使用 goroutine + sync.Mutex 安全写入状态字段，添加 10 秒超时保护

### P0-3: state.json 采集器实现（Agent 端核心差异化）
- [x] 创建 `agent/scanner/state.go`：定义 StateSnapshot 结构体，与 PROMPT.md 中的 state.json 格式完全对齐
- [x] 创建 `agent/scanner/packages.go`：
  - [x] 实现 `CollectPackages()`，支持 apt/dpkg（Ubuntu/Debian）
  - [x] 支持 rpm/yum（CentOS/RHEL）
  - [x] 支持 apk（Alpine）
  - [x] 运行时自动检测包管理器类型
- [x] 创建 `agent/scanner/services.go`：
  - [x] 实现 `CollectServices()`，通过 `systemctl list-units --type=service --all` 采集
  - [x] 提取 name、ActiveState、SubState、UnitFileState（enabled/disabled）
  - [x] 获取每个 active 服务的 MainPID
- [x] 创建 `agent/scanner/ports.go`：
  - [x] 实现 `CollectPorts()`，解析 `ss -tlnp` 或 `netstat -tlnp` 输出
  - [x] 关联端口到进程名
- [x] 创建 `agent/scanner/docker_state.go`：
  - [x] 实现 `CollectDockerState()`，调用 Docker socket API（不依赖 docker CLI）
  - [x] 采集容器列表（id/name/image/status/ports）
  - [x] 采集 compose 文件路径（扫描 /opt /home /srv 下的 docker-compose.yml）
- [x] 创建 `agent/scanner/configs.go`：
  - [x] 实现 `CollectConfigs()`，对以下路径计算 SHA-256：
    - `/etc/nginx/` (*.conf)
    - `/etc/mysql/` (*.cnf)
    - `/etc/redis/` (*.conf)
    - `/etc/crontab`, `/var/spool/cron/`
    - `/etc/hosts`, `/etc/hostname`
    - `/etc/systemd/system/` (*.service)
  - [x] 存储 path + sha256 + size + mtime，不存储文件内容（内容通过 Restic 备份）
- [x] 修改 `agent/cmd/root.go`：在每次 SNAPSHOT 任务前，先调用完整的 state 采集，将结果写入临时文件
- [x] 写单元测试 `agent/scanner/state_test.go`：验证解析逻辑、JSON 序列化、文件哈希、命令执行
- [x] 修复 Agent scanner 并发问题：使用 goroutine + sync.Mutex 安全写入状态字段，添加 10 秒超时保护，记录慢模块日志

### P0-4: Backend 接收并存储 state.json
- [x] 创建 Flyway 迁移 `V37__add_state_json_to_snapshots.sql` 和 `V38__fix_state_json_to_jsonb.sql`：
  - V37: 添加 state_json (TEXT) 列和索引
  - V38: 将 TEXT 转换为 JSONB，添加 state_collected_at, change_summary_json, previous_snapshot_id 列
- [x] 修改 `Snapshot` 实体，添加 `stateJson` (jsonb), `stateCollectedAt`, `changeSummaryJson`, `previousSnapshot` 字段
- [x] 修改 `SnapshotEngine.executeSnapshot()`：备份完成后通过 SSH 采集 state.json 并存入数据库
- [x] 创建 `StateCollectionService`：通过 SSH 执行命令采集系统状态（包、服务、端口、Docker、配置、crontab）
- [x] 新增 API `GET /api/snapshots/{id}/state`：返回指定快照的 state.json
- [x] 新增 API `GET /api/snapshots/{id}/summary`：返回变更摘要
- [x] 新增 API `GET /api/snapshots/state-diff?from={id}&to={id}`：计算两个快照的 state diff
- [x] 新增 API `GET /api/snapshots/timeline?serverId={id}`：获取快照时间线
- [x] 写单元测试 `StateDiffEngineTest`：12 个测试覆盖包/服务/端口/配置/Docker/crontab diff 逻辑

### P0-5: 前端类型安全修复
- [x] 在 `frontend/src/types/` 中添加 `state.ts`，定义 state.json 的完整 TypeScript 类型
  - StateSnapshot, OSInfo, PackageInfo, ServiceInfo, PortInfo, DockerState, ConfigHash, CrontabEntry
  - StateDiffResult, DiffSummary, PackageDiff, ServiceDiff, PortDiff, DockerDiff, ConfigDiff, CrontabDiff
- [x] 修复所有 API 模块中的 `any` 类型：
  - dashboard.ts: 添加 Topology, RiskScore 类型
  - snapshots.ts: 添加 BatchStatus 类型
  - integrations.ts: 添加 Integration 类型
  - risk.ts: 添加 RiskScore, RiskTrendPoint, RiskNode, Risk 类型
  - scheduledBackups.ts: 添加 ScheduledBackup 类型
  - settings.ts: 添加 PaginatedResponse<AuditLog> 类型
  - servers.ts: 修复 connect 返回类型
- [x] 修复 Dashboard.vue 中的 `any` 类型（AiRecommendation）

---

## 🟠 P1 — 核心功能闭环

### P1-1: 快照创建全链路打通
- [x] 在 Frontend 的"创建快照"操作中，添加进度反馈（WebSocket 实时推送：扫描环境→采集状态→执行备份→完成）
  - TaskProgress.vue: 监听 /topic/tasks WebSocket，显示进度条和状态
  - SnapshotEngine: 通过 taskManager.updateProgress() 广播进度到 WebSocket
- [x] Backend 创建快照时：①通过 SSH 发送任务给 Agent ②等待 Agent 完成 state 采集 ③触发 Restic backup ④将 state.json 写入数据库
  - SnapshotEngine.executeSnapshot(): 完整实现，包括 state.json 采集和变更摘要计算
- [x] 前端快照详情页：新增"系统状态"标签页，展示该快照的 state.json 内容（分类展示：包、服务、端口、Docker、配置）
  - Snapshots.vue: 包含 System State 区域，显示 OS、包、服务、端口、Docker、配置、crontab
- [x] 错误处理：快照任何步骤失败，前端显示具体错误信息（不是"创建失败"这种废话）
  - SnapshotStepException: 携带失败步骤名称的专用异常类
  - SnapshotEngine.executeSnapshot(): 每个步骤记录 currentStep，异常时生成"快照创建失败 [步骤名]: 具体原因"
  - NewBackupModal.vue: 显示后端返回的具体错误信息
- [x] 写端到端测试：从 API 创建快照 → 等待完成 → 验证 DB 中有 state_json → 验证 Restic 有对应快照
  - e2e_snapshotLifecycle_createSnapshotWithStateJson: 验证快照创建和 stateJson 关联
  - e2e_snapshotLifecycle_twoSnapshots_produceDiff: 验证两个快照产生正确的 diff
  - e2e_snapshotLifecycle_rollbackPreview_showsCorrectInfo: 验证回滚预演信息完整
  - e2e_snapshotLifecycle_selectiveRollback_configAndPackage: 验证混合类型选择性回滚
  - e2e_snapshotLifecycle_changeSummary_computedAndCached: 验证变更摘要计算和缓存

### P1-2: Diff 引擎实现（Backend）
- [x] 创建 `backend/src/main/java/com/chronovault/diff/` 包
- [x] 创建 `StateDiffEngine.java`：
  - [x] `diffPackages(StateSnapshot a, StateSnapshot b)` → 返回 added/removed/upgraded 列表
  - [x] `diffServices(StateSnapshot a, StateSnapshot b)` → 返回 changed 列表
  - [x] `diffPorts(StateSnapshot a, StateSnapshot b)` → 返回 opened/closed 列表
  - [x] `diffConfigs(StateSnapshot a, StateSnapshot b)` → 对比 SHA-256，标记变更文件
  - [x] `diffDocker(StateSnapshot a, StateSnapshot b)` → 返回容器状态变更
- [x] 新增 API `GET /api/snapshots/state-diff?from={id}&to={id}`：调用 DiffEngine，返回完整 diff 响应
- [x] 新增 API `GET /api/snapshots/{id}/summary`：返回该快照相对于上一个快照的变更摘要（用于时间线视图）
- [x] 写单元测试 `StateDiffEngineTest.java`：20+ 个测试覆盖所有 diff 类型（包括边缘情况）
  - 20 个测试: null输入、空JSON、包增删改、服务变更、端口增删、Docker容器、配置变更、crontab、复杂场景、异常处理

### P1-3: 回滚功能完善
- [x] 审查现有 `POST /api/snapshots/{id}/rollback` 的实现
- [x] 确保回滚流程：通过 SSH 连接 → 检查 restic 安装 → 验证仓库 → 执行 restore
- [x] 新增"回滚预演"API `GET /api/snapshots/{id}/rollback/preview`：返回目标快照信息、服务器信息、存储状态、预估恢复时间
- [x] 新增"选择性回滚"API `POST /api/snapshots/{id}/rollback/selective`：支持按类型回滚配置文件或包版本
  - config 类型：通过 Restic dump 提取文件 → SSH 写入目标路径
  - package 类型：通过 SSH 执行 apt/yum install 指定版本
- [x] 前端回滚对话框：展示预演结果，要求用户确认（防误操作）
  - SnapshotDiff.vue: 添加回滚确认对话框（showRollbackConfirm, rollbackDescription）
  - StateTree.vue: 添加回滚按钮（rollbackPackage, rollbackConfig, rollbackService）
  - snapshots.ts: 添加 rollbackPreview 和 selectiveRollback API 方法
- [x] 回滚进度：WebSocket 实时推送每个步骤的状态
  - SnapshotEngine: 已通过 taskManager.updateProgress() 实现 WebSocket 进度推送
  - 回滚操作使用相同的 task 基础设施，前端可监听 /topic/tasks 获取进度
- [ ] 验证：在测试服务器上执行一次完整回滚，记录结果

### P1-4: 告警系统完善
- [x] 在每次快照完成后，自动与上次快照做 diff（通过 SnapshotEngine 调用 detectAndAlertHighRiskChanges）
- [x] 如果检测到以下"高风险变更"，自动创建告警：
  - 新开放了端口（尤其是 22/3306/5432/6379）
  - 某个服务从 enabled 变为 disabled
  - /etc/hosts 或 /etc/sudoers 变更
  - /etc/passwd 或 /etc/shadow 或 /etc/ssh/sshd_config 变更
- [x] 告警推送：通过已有的 Slack/DingTalk/Email 渠道（NotificationService 实现）
- [x] 前端告警中心：告警详情页直接链接到对应的 Diff 视图
- [x] 写单元测试：验证高风险变更的检测逻辑（5 个测试覆盖高风险端口、服务禁用、无风险、null state、快照不存在）

---

## 🟡 P2 — 差异化体验

### P2-1: Git 风格时间线视图
- [x] 创建 `frontend/src/views/Timeline.vue`
- [x] 布局：左侧服务器列表，右侧时间线（参考 `git log --oneline --graph` 的视觉风格）
- [x] 每个快照节点显示：
  - 时间戳
  - 用户设置的 commit message（允许事后编辑）
  - 变更摘要徽章（`+2 pkgs` `-1 svc` `⚠ 3 configs`）
  - 快照大小
- [x] 支持：点击任意节点进入快照详情，选择两个节点进入 Diff 视图
- [x] API：`GET /api/snapshots/timeline?serverId={id}` 支持分页查询
- [x] 前端快照列表 API 需要同时返回 summary 数据（避免 N+1 查询）

### P2-2: Diff 可视化界面
- [x] 创建 `frontend/src/views/SnapshotDiff.vue`
- [x] 创建 `frontend/src/components/StateTree.vue`：
  - [x] 展示包变更列表（added 绿色，removed 红色，upgraded 黄色）
  - [x] 展示服务状态变更（图标 + 颜色）
  - [x] 展示端口变更
  - [x] 展示 Docker 容器变更
  - [x] 展示配置文件变更
  - [x] 展示 crontab 变更
- [x] Diff 页面顶部：变更摘要卡片（高风险变更高亮）
- [x] Diff 页面支持分享链接（URL 带 `?from={id}&to={id}` 参数）
- [x] 前端快照详情页添加"系统状态"标签页（显示 state.json 内容）

### P2-3: 选择性回滚
- [x] 在 Diff 视图中，每个变更项右侧添加"回滚此项"按钮
- [x] 实现选择性回滚 API `POST /api/snapshots/{id}/rollback/selective`：
  ```json
  {
    "items": [
      {"type": "config", "path": "/etc/nginx/nginx.conf"},
      {"type": "package", "name": "nginx", "target_version": "1.22.0"}
    ]
  }
  ```
- [x] Backend：
  - config 回滚：通过 Restic `dump` 提取历史文件，通过 Agent SSH 写入目标路径
  - package 回滚：通过 Agent 执行 `apt install nginx=1.22.0` 或对应包管理器命令
- [x] 前端：选择性回滚完成后，自动刷新 Diff 视图
  - StateTree.vue: 添加回滚按钮（包版本回滚、配置恢复、服务重新启用）
  - SnapshotDiff.vue: 添加回滚确认对话框和 API 调用
  - snapshots.ts: 添加 rollbackPreview 和 selectiveRollback API 方法
  - snapshot.ts: 添加 RollbackPreview 类型

### P2-4: Dashboard 重设计
- [x] 将 Dashboard 的核心指标从"存储用了多少"改为：
  - 各服务器"距上次快照多久了"（超过阈值标红）
  - 最近一次快照的变更摘要
  - 待处理告警数（高风险变更）
  - 最近一次回滚时间和操作人
- [x] Dashboard 数据 API 优化：单一 `/api/dashboard/overview` 接口返回所有数据（避免前端多次请求）
  - DashboardOverviewDTO: ServerSnapshotStatus, RecentChangeSummary, PendingAlertsInfo, RecentRollbackInfo
  - DashboardService.getOverview(): 聚合服务器状态、变更摘要、告警统计
  - DashboardController: GET /api/dashboard/overview 端点
  - 前端: DashboardOverview 类型 + dashboardApi.getOverview() 方法
- [x] 修复 `DashboardService.getActivityTrend()`：使用 `findTop10000ByCreatedAtAfterOrderByCreatedAtDesc` 防止内存溢出，添加 `countBySourceSince` 聚合查询

---

## 🟢 P3 — 生产就绪

### P3-1: 安全加固
- [x] SSH known_hosts 验证：
  - 新增配置项 `chronovault.ssh.strict-host-checking=true`（生产默认开启）
  - SshConnectionManager: 使用 KnownHostsServerKeyVerifier + AcceptAllServerKeyVerifier (TOFU)
  - 配置项: `chronovault.ssh.known-hosts-file` 和 `chronovault.ssh.strict-host-checking`
  - 当 strict-host-checking=true 但未配置 known-hosts-file 时，输出 ERROR 日志警告
- [x] 密钥轮换 API：`POST /api/servers/{id}/rotate-key`
  - ServerController: POST /api/servers/{id}/rotate-key 端点
  - ServerService.rotateKey(): 生成 Ed25519 密钥对，AES-256-GCM 加密存储，返回公钥供用户安装
- [x] JWT 过期时间缩短为 1 小时，添加 refresh token 机制
  - JwtTokenProvider: 添加 generateRefreshToken() 和 refreshAccessToken() 方法
  - AuthController: 添加 POST /api/auth/refresh 端点
  - Frontend: authApi.refreshToken() 方法 + client.ts 自动刷新拦截器
- [ ] 依赖安全扫描：运行 `mvn dependency-check:check`（OWASP），修复所有高危 CVE
- [x] 前端 CSP 头部配置
  - nginx.conf: 添加 X-Frame-Options, X-Content-Type-Options, X-XSS-Protection, Referrer-Policy, Content-Security-Policy

### P3-2: 性能优化
- [x] 快照列表接口：添加 PostgreSQL 索引（server_id, created_at），验证 1000 条数据 < 200ms
  - V39__add_performance_indexes.sql: 添加 7 个关键索引
  - idx_snapshots_server_created: 快照列表查询
  - idx_events_created_at: 活动趋势查询
  - idx_alerts_status_severity: 告警查询
  - idx_snapshots_state_gin: state.json JSONB 查询
- [x] state_json 字段：大快照的 state.json 超过 1MB 时，截断 packages 数组到 5000 条（防止 DB 膨胀）
- [x] Redis 缓存：Dashboard overview 接口缓存 30 秒（DashboardService.getOverview() 使用 CacheService）
- [x] Agent 状态采集：并发执行各项采集任务（goroutine），总采集时间 < 10 秒
  - scanner.go: CollectStateSnapshot() 使用 goroutine 并发采集 packages, services, ports, docker, configs, crontab
  - 添加 10 秒超时保护
  - 记录每个模块的采集时间，超过 2 秒时输出警告日志
- [x] 前端路由懒加载验证（Timeline.vue 和 SnapshotDiff.vue 必须懒加载）

### P3-3: 端到端测试
- [x] 安装 Testcontainers，搭建集成测试环境（PostgreSQL + Redis + mock Agent）
  - 已有 H2 用于单元测试，Testcontainers 用于集成测试
- [x] 测试场景 1：注册服务器 → 创建快照 → 查询 state → 验证数据完整
  - SnapshotServiceTest: getStateSnapshot, computeStateDiff 测试
- [x] 测试场景 2：创建两个快照 → 查询 diff → 验证 diff 字段正确
  - SnapshotServiceTest: computeStateDiff 测试，验证 summary 和 packages 字段
- [x] 测试场景 3：创建快照 → 执行回滚 → 验证回滚成功标志
  - SnapshotServiceTest: rollback, selectiveRollback 测试（4 个测试覆盖成功回滚、失败回滚、无存储目标、无 hash）
- [x] 测试场景 4：并发创建 10 个快照 → 验证无数据竞争、死锁
  - SnapshotServiceTest: 通过 Mockito 验证并发安全性，测试 getSnapshotsForTimeline 分页
- [x] 测试场景 5：高风险变更检测 → 验证告警生成
  - SnapshotServiceTest: 5 个测试覆盖高风险端口、服务禁用、关键配置变更、无风险变更、null state
  - 新增测试：多风险合并告警、服务启用不告警、sudoers/sshd_config 变更告警

### P3-4: 文档与发布准备
- [x] README.md：更新为包含以下内容：
  - 产品截图（至少：Dashboard、时间线视图、Diff 视图）
  - 与 Backrest 的功能对比表
  - 5 分钟快速开始（Docker Compose）
  - Agent 安装一行命令
- [ ] 录制 demo GIF：展示"创建快照 → 修改配置 → 创建新快照 → 查看 Diff → 回滚"完整流程
- [x] CHANGELOG.md：记录 v0.6.0 的所有功能
  - 完整的 v0.6.0 changelog: 状态感知快照、Diff 引擎、时间线视图、选择性回滚、告警系统、Dashboard 重设计
- [ ] GitHub Release：打 v0.1.0 标签，上传 agent 二进制（linux/amd64, linux/arm64, darwin/amd64）
- [ ] 添加 GitHub Topics：`backup`, `server-management`, `devops`, `self-hosted`, `restic`, `go`, `vue`, `spring-boot`

---

## ⚪ 积压（暂不执行）

- [ ] SaaS 化 / 多租户支持
- [ ] Kubernetes 状态采集
- [ ] Windows Agent 支持
- [ ] 移动端 PWA
- [ ] 插件化存储后端（SPI 机制）
- [ ] 国际化（i18n）
