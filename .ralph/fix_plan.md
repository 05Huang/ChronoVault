# ChronoVault — Ralph 任务执行计划 v2.0

> 每完成一个任务，将 `[ ]` 改为 `[x]`。
> Ralph 按照优先级从上到下执行，不跳过未完成的任务。
> **P0 全部完成前不进入 P1，P1 全部完成前不进入 P2。**
> 每个任务完成后执行验证：`mvnw.cmd test -f backend/pom.xml` + `cd frontend && npx vue-tsc --noEmit`
> 验证通过后 git commit，格式：`feat/fix/refactor/chore(scope): 描述`

---

## 🔴 P0 — 安全与数据完整性（最高优先级）

### P0-1: 输入验证与防注入
- [x] 为所有 `@RequestBody` DTO 添加 `@NotBlank`、`@Size`、`@Pattern` 等 Jakarta Validation 注解（检查 `CreateSnapshotRequest`、`LoginRequest`、`RegisterRequest`、`CreateBranchRequest` 等所有 DTO）
- [x] `SnapshotController.exportSnapshots()` 手动构建 CSV/JSON/YAML 时存在注入风险，改用 Jackson 的 `ObjectMapper` 或 OpenCSV 库序列化
- [x] `SnapshotController.selectiveRollback()` 接收原始 `Map<String, Object>` 无类型安全，创建 `SelectiveRollbackRequest` DTO 替代
- [x] `SnapshotController.batchTag()` 同理，创建 `BatchTagRequest` DTO
- [x] `SnapshotController.batchDelete()` 和 `startBatch()` 接收原始 `List<Long>`/`Map`，创建专用 Request DTO
- [x] `ServerController` 中所有接收 `Map<String, Object>` 的端点，全部替换为类型安全的 Request DTO
- [x] 全局搜索 `Map<String, Object>` 和 `Map<String, String>` 作为 `@RequestBody` 参数的情况，逐一替换
- [x] 为 `name`、`title`、`note`、`description` 等字段添加 XSS 过滤（创建 `SanitizeUtil` 工具类，转义 HTML 特殊字符）

### P0-2: 安全漏洞修复
- [x] `SecurityConfig` 中 `/ws/**` 全部 `permitAll()` 过于宽泛，缩小为仅 `/ws/events` 和 `/ws/topics/**`，其余需要认证
- [x] `SecurityConfig` 中 `/swagger-ui.html` 和 `/v3/api-docs/**` 在生产环境（prod profile）应禁用，通过 `@Profile("!prod")` 或配置化控制
- [x] `JwtTokenProvider` 检查密钥长度是否至少 256 位，添加启动校验
- [x] `CredentialEncryptor` 验证 master key 长度校验，短于 32 字节时拒绝启动
- [x] `RateLimitFilter` 确认限流策略是否生效（检查 Redis key 过期逻辑），添加 IP + 用户维度限流
- [x] `ApiKeyAuthenticationFilter` 中 API key 的查询不应每次都查库，添加 Redis 缓存（TTL 5 分钟）
- [x] 所有 Controller 中 `Authentication auth` 获取的 `auth.getName()` 应添加 null 检查，防止认证信息缺失时 NPE
- [x] 审查所有 `log.info()` 中是否意外打印了密码、密钥、token 等敏感信息，创建 `SensitiveDataMasker` 工具类
- [x] 检查 `application-dev.yml` 和 `application-prod.yml` 中密码是否在日志中明文输出（`show-sql: true` 在 dev 中可能泄露数据）

### P0-3: 数据库事务与并发安全
- [x] `SnapshotService.getSnapshotsByTag()` 中 `findAll()` 全量加载所有快照再过滤，改为 JPQL 联表查询 `JOIN tags WHERE tag.name = ?`
- [x] `SnapshotService.getSnapshotDiff()` 中 `findByServerIdOrderByCreatedAtDesc()` 全量加载后 `stream().filter()`，改为分页查询
- [x] `SnapshotService.rollback()` 方法标注了 `@Transactional` 但内部 SSH 操作在事务外执行，拆分为事务内（更新状态）和事务外（SSH 操作）两个方法
- [x] `AutoSnapshotService` 中 `serverRepository.findAll()` + `stream().filter()`，改为 `findAllByAutoSnapshotEnabled(true)` 查询
- [x] `AiService` 中多处 `findAll()` 全量加载，添加分页或限制查询范围
- [x] `DashboardService` 检查所有查询是否都有分页保护，防止数据增长后 OOM
- [x] `AlertService.getAlerts()` 调用 `findAllByOrderByCreatedAtDesc()`，改为分页查询
- [x] `AsyncTaskRepository.findAllByOrderByCreatedAtDesc()` 和 `EventRepository.findAllByOrderByCreatedAtDesc()` 都需要添加分页

### P0-4: 异常处理完善
- [x] `SnapshotService.rollback()` catch 块中抛出 `RuntimeException`，改为抛出自定义 `RollbackFailedException` 并包含失败原因枚举
- [x] `SnapshotService.createSnapshot()` catch 块中 `throw new BadRequestException("快照创建失败: " + e.getMessage())` 会暴露内部错误，改为通用错误 + 记录详细日志
- [x] `StateDiffEngine.diff()` catch 块中 `log.warn` 后返回空 Map，调用方无法区分"无差异"和"计算失败"，返回包含 `error` 字段的结构化结果
- [x] `CacheService` 所有方法 catch 后静默吞异常，至少在 debug 级别记录完整堆栈
- [x] `AuditLogAspect` 中 catch `Exception` 后仅 `log.warn`，审计日志失败应升级为 `log.error` 并发送告警
- [x] `GlobalExceptionHandler` 添加 `ConstraintViolationException` 处理器（处理 `@RequestParam` 验证失败）
- [x] `GlobalExceptionHandler` 添加 `DataIntegrityViolationException` 处理器（数据库约束冲突，返回友好的 409 错误）
- [x] `GlobalExceptionHandler` 添加 `HttpMessageNotReadableException` 处理器（JSON 反序列化失败，返回友好的 400 错误）

---

## 🟠 P1 — 后端代码质量与性能

### P1-1: 结构化日志体系
- [x] 为所有 Service 类统一 `log.info/warn/error` 格式：`[操作类型] [目标对象ID] 结果描述`，例如 `[SNAPSHOT_CREATE] [server=12] 快照创建成功`
- [x] 创建 `LogContextFilter`（Web Filter），为每个请求注入 `requestId`、`userId`、`clientIp` 到 MDC，日志自动携带请求上下文
- [x] `SnapshotEngine.executeSnapshot()` 中每个步骤（连接→检查工具→初始化仓库→备份→采集状态→保存）都要有 `log.info` 标记步骤开始和结束，耗时超过阈值时输出 `log.warn`
- [x] `SshConnectionManager` 添加连接池指标日志：创建连接数、复用连接数、销毁连接数、空闲连接数（每 60 秒输出一次）
- [x] `ResticClient` 每个操作记录执行耗时（init/backup/restore/diff/stats/forget），输出到日志和 Micrometer 指标
- [x] `AsyncTaskManager` 记录任务生命周期：创建→排队→开始执行→进度更新→完成/失败，包含耗时
- [x] 创建 `logback-spring.xml` 生产配置：JSON 格式输出、按天滚动、保留 30 天、压缩 7 天以上日志
- [x] 为所有 `@Async` 方法添加方法级别的 `log.info` 记录入参和出参（脱敏后）

### P1-2: 性能优化 — 查询与缓存
- [x] 创建 `SnapshotRepository` 自定义查询方法 `findPageWithTags(int page, int size)` 使用 `@Query` 联表查询避免 N+1
- [x] `SnapshotService.getSnapshotsPaged()` 中每个 Snapshot 都单独查 tags，改为 JOIN FETCH 或 `@BatchSize`
- [x] `ServerController.getServers()` 或列表接口添加服务器状态缓存（Redis TTL 30 秒）
- [x] `DashboardService.getOverview()` 结果缓存 60 秒，服务器列表变更时主动失效
- [x] `StorageTargetRepository.findAll()` 在 `SnapshotService.rollback()` 和 `createSnapshot()` 中每次调用，改为按 ID 查询或缓存
- [x] `SnapshotRepository` 添加复合索引：`(server_id, created_at DESC)` — 验证已有的 V39 索引是否覆盖时间线查询
- [x] `EventRepository` 和 `AuditLogRepository` 添加分页方法 `findByCreatedAtBetween(Pageable, start, end)`，替代全量查询
- [x] `AiInsightRepository` 和 `AiRecommendationRepository` 添加分页方法，限制返回条数
- [x] `ContainerStateRepository` 查询优化：`findBySnapshotIdOrderByContainerNameAsc` 已有，确认是否有 DB 索引
- [x] Redis 缓存策略统一化：创建 `CacheKeyBuilder` 工具类，统一 key 前缀和 TTL 管理

### P1-3: API 设计规范化
- [x] 所有列表 API 统一分页参数：`page`（从 0 开始）、`size`（默认 20，最大 100）、`sort`、`direction`
- [x] 所有列表 API 返回统一的 `PageResponse<T>` 结构（已定义在 `GlobalExceptionHandler.PageResponse`，需要所有端点都使用）
- [x] `SnapshotController.getSnapshots()` 中混合了分页和非分页逻辑，拆分为两个端点：`GET /api/snapshots`（分页）和 `GET /api/snapshots/all`（全量，仅限小数据量）
- [x] 为所有 POST/PUT 端点的 Response 添加 `Location` 头：`ResponseEntity.created(URI).body()`
- [x] `SnapshotController.exportSnapshots()` 改为异步导出（大数据量时先返回任务 ID，完成后通知下载），避免长时间 HTTP 连接
- [x] 创建 `@ApiVersion` 或 URL 前缀 `/api/v1/` 为未来 API 升级预留空间
- [x] 所有 `@Operation` 注解添加 `summary` 和 `description`（当前部分端点缺失 Swagger 文档）
- [x] 为所有 4xx/5xx 响应添加 `@ApiResponse` 注解（Swagger 文档中展示错误码和错误消息格式）
- [x] 创建 `ErrorCode` 枚举类，统一所有错误码（NOT_FOUND=40401, BAD_REQUEST=40001, UNAUTHORIZED=40101 等），替换硬编码的 `ApiResponse.error(404, "快照不存在")`

### P1-4: 审计日志增强
- [x] `@Auditable` 注解扩展：添加 `resourceType`（SERVER/SNAPSHOT/STORAGE/ALERT 等）和 `resourceId` 字段
- [x] `AuditLogAspect` 在切面中提取资源 ID（通过 `@Auditable` 注解参数或方法参数名匹配），存入审计记录
- [x] 为以下操作添加 `@Auditable` 注解（当前缺失）：登录/登出、存储目标增删改、Webhook 增删改、定时备份增删改、告警规则增删改、用户角色变更
- [x] `AuditLogRepository` 添加查询方法：`findByResourceTypeAndResourceId(resourceType, resourceId)` — 查看某个资源的操作历史
- [x] 审计日志导出功能：`GET /api/audit/export` 支持 CSV 格式导出（按时间范围筛选）
- [x] 创建 `AuditLogRetentionScheduler`，定期清理超过 90 天的审计日志（归档到冷存储或删除）
- [x] `AuditLog` 实体添加 `ipAddress` 和 `userAgent` 字段（已有的 `AuditLogAspect` 通过 `RequestContextHolder` 获取，确认是否已存入）

### P1-5: SSH 连接池加固
- [x] `SshConnectionManager.getConnection()` 中连接创建失败时，添加指数退避重试（最多 3 次，间隔 1s/2s/4s）
- [x] 添加连接健康检查定时任务（每 60 秒），对空闲超过 5 分钟的连接执行 `echo ok` 验证，失效则关闭
- [x] `SshConnectionManager` 添加 `getConnectionCount()` 和 `getActiveConnectionCount()` 方法供健康检查使用
- [x] `SshConnection` 添加 `lastUsedAt` 时间戳，idle eviction 定时任务根据此字段清理过期连接
- [x] 连接池添加最大连接数限制（全局），超过限制时等待而非创建新连接，避免目标服务器 SSH 限流
- [x] `SshConnectionManager.close()` 添加优雅关闭逻辑：等待正在执行的命令完成后再关闭连接
- [x] 记录每次 SSH 命令执行的耗时和结果（成功/失败），存入 Redis 供 Dashboard 展示连接质量指标

### P1-6: 快照引擎可靠性提升
- [x] `SnapshotEngine.executeSnapshot()` 添加总超时控制（默认 30 分钟），超时后自动取消任务并清理
- [x] `resticClient.backup()` 返回值中解析 Restic 的 `files_new`、`files_changed`、`bytes_added` 等统计信息，存入 `Snapshot.sizeBytes`
- [x] `SnapshotEngine` 中 `restic init` 添加幂等检查：先执行 `restic snapshots` 判断仓库是否已初始化
- [x] `SnapshotEngine` 在备份完成后验证：执行 `restic check` 确保仓库完整性，结果记录到日志
- [x] `SnapshotEngine` 添加 pre-flight 检查：连接服务器后先检查磁盘空间（`df -h /`），空间不足时拒绝备份并给出提示
- [x] `SnapshotEngine.createSnapshot()` 中 `snapshotEngine.createSnapshot()` 被 `SnapshotService` 调用时传入了硬编码的 title（`"快照 " + LocalDateTime.now()`），应优先使用用户传入的 title/note
- [x] `ResticClient` 中处理 Restic exit code 3（部分成功）的场景，记录具体哪些文件备份失败

### P1-7: 状态采集增强
- [x] `StateCollectionService` 添加超时控制：每个采集模块（packages/services/ports/docker/configs/crontab）独立超时 10 秒，超时后跳过并标记为 `timeout`
- [x] `StateCollectionService` 采集结果添加 `collection_duration_ms` 字段，记录每个模块耗时
- [x] Agent scanner 添加 `system_info` 采集：主机名、IP 地址、内存使用率、磁盘使用率、CPU 核心数、系统运行时间
- [x] Backend diff 引擎 `StateDiffEngine` 添加 `crontab` diff 逻辑（当前 `diffCrontab` 方法实现是否完整需验证）
- [x] `StateDiffEngine` 添加 `os` diff：对比两次快照的操作系统版本、内核版本是否有变化（内核升级是高风险操作）
- [x] 变更摘要（`change_summary_json`）自动生成：每次快照完成后自动计算并缓存，避免前端请求时实时计算

### P1-8: 定时任务与调度
- [x] `AutoSnapshotService` 中 `@Scheduled` 定时任务添加分布式锁（Redis `SETNX`），防止多实例重复执行
- [x] `ScheduledBackupService` 调度器验证：确认 cron 表达式解析是否正确，添加 `next_run_at` 计算逻辑
- [x] 创建 `HealthCheckScheduler`：每 5 分钟检查所有服务器 SSH 连通性，不连通时创建告警
- [x] 创建 `RetentionEnforcer`：每天凌晨 2 点按 RetentionPolicy 清理过期快照和对应的 Restic 仓库数据
- [x] `AutoSnapshotService` 中 `findStaleServers()` 逻辑验证：超过阈值未快照的服务器检测是否正确工作
- [x] 所有 `@Scheduled` 任务添加执行日志和耗时统计，异常时不崩溃（catch + log.error）

---

## 🟡 P2 — 前端质量与用户体验

### P2-1: 前端类型安全
- [x] `useWebSocket.ts` 中 `// @ts-ignore` 注释和 `message.body as any`，修复 SockJS/STOMP 类型定义（创建 `types/stomp.d.ts` 声明文件）
- [x] 审查所有 `api/*.ts` 模块的返回类型，确保没有 `any` 类型暴露给组件层
- [x] `api/client.ts` 中响应拦截器的 `body.data` 解析逻辑，添加类型守卫：检查 `data` 是否为 `null` 或 `undefined` 的情况
- [x] 所有 Vue 组件的 `defineProps()` 添加 TypeScript 类型声明（非运行时声明）
- [x] `stores/modal.ts` 中动态模态框的组件注册添加类型约束，避免运行时组件找不到

### P2-2: 前端错误处理
- [x] 创建 `api/errorHandler.ts`：统一处理 401（跳转登录）、403（显示无权限提示）、404（显示资源不存在）、500（显示服务器错误）、网络错误（显示网络异常）
- [x] `api/client.ts` 中 401 处理已有 refresh token 逻辑，验证 refresh token 也过期时的降级处理是否完善
- [x] 为所有页面组件添加 `onErrorCaptured` 钩子，捕获子组件渲染错误并展示友好的错误边界 UI（创建 `ErrorBoundary.vue` 组件）
- [x] 所有 API 调用添加 loading 状态管理（创建 `useLoading` composable 或全局 store），避免用户重复点击
- [x] 所有表单提交添加防重复提交逻辑（提交按钮在请求期间 disabled）
- [x] 创建 `ToastNotification` 组件：成功/警告/错误/信息四种类型，统一前端消息提示

### P2-3: 前端性能优化
- [x] `Dashboard.vue` 验证数据加载是否使用 `Promise.all` 并行请求，避免串行请求导致的瀑布式加载
- [x] `Snapshots.vue` 列表大数据量时添加虚拟滚动（使用 `vue-virtual-scroller` 或手动实现）
- [x] `Timeline.vue` 时间线视图添加懒加载：滚动到底部时加载更多（Intersection Observer）
- [x] 所有图片和大列表添加 loading skeleton（骨架屏组件），避免页面空白
- [x] `StateTree.vue` 大量变更项时添加折叠/展开功能，默认折叠已知安全的变更
- [x] 前端路由切换时添加页面过渡动画（`<router-view>` 包裹 `<Transition>`）
- [x] 为 `useWebSocket.ts` 的 STOMP 连接添加断线重连逻辑（当前实现是否有自动重连需验证）

### P2-4: UI/UX 改进
- [x] `SideNavBar.vue` 添加当前页面高亮指示（路由匹配时高亮对应菜单项）
- [x] `TopNavBar.vue` 添加面包屑导航，让用户知道当前所在位置
- [x] `Dashboard.vue` 添加"快速操作"区域：一键创建快照、一键添加服务器、查看最新告警
- [x] 快照详情页添加"创建时间线"操作按钮：从任意快照节点开始展示时间线
- [x] `SnapshotDiff.vue` 添加键盘快捷键：`j`/`k` 上下切换变更项，`r` 回滚选中项
- [x] 所有删除操作添加二次确认弹窗（现有 `ConfirmModal` 确认是否所有删除都使用了）
- [x] `ServerDetail.vue` 添加服务器健康度仪表盘（综合快照频率、告警数量、磁盘使用率等指标）
- [x] 前端深色/浅色主题切换功能（Tailwind CSS 4 支持 dark mode class 切换）
- [x] 创建 `LoadingSpinner.vue` 和 `EmptyState.vue` 通用组件，统一所有页面的加载和空数据状态
- [x] `Login.vue` 和 `Register.vue` 添加表单验证实时提示（邮箱格式、密码强度、密码一致性）

### P2-5: 前端测试
- [x] 安装 Vitest + `@vue/test-utils`，配置前端单元测试框架
- [x] 为 `api/client.ts` 编写单元测试：验证 token 刷新逻辑、401 重定向、响应数据解包
- [x] 为 `stores/auth.ts` 编写单元测试：验证登录状态管理、token 存储、登出清理
- [x] 为 `StateDiffEngine` 的前端展示逻辑编写测试：验证 diff 数据到 UI 的转换
- [x] 创建 E2E 测试框架（Playwright），编写核心流程测试：登录→添加服务器→创建快照→查看 Diff

---

## 🟢 P3 — Agent 质量与可靠性

### P3-1: Agent 错误处理
- [x] `agent/cmd/root.go` 中 `executeTask()` 添加 panic recovery，防止 goroutine panic 导致 Agent 崩溃
- [x] `agent/restic/client.go` 中每个 Restic 命令添加 stderr 输出捕获和结构化错误分类（网络错误、权限错误、仓库损坏、磁盘空间不足等）
- [x] `agent/transport/client.go` 中 HTTP 请求添加重试逻辑（最多 3 次，指数退避），处理 Backend 暂时不可用的情况
- [x] Agent 启动时验证：Restic 是否已安装、Backend 是否可达、配置文件是否有效，失败时给出明确的修复建议
- [x] `agent/scanner/` 所有采集器添加 `context.WithTimeout` 控制（单个采集器 10 秒超时），防止系统命令挂起

### P3-2: Agent 健康上报
- [x] Agent 添加 `/health` 端口（默认 8081），Backend 可通过此端口检查 Agent 是否在线
- [x] Agent 定期（每 60 秒）向 Backend 发送心跳：包含 Agent 版本、操作系统信息、磁盘空间、Restic 版本
- [x] `AgentCommunicationService`（Backend 端）实现 Agent 在线状态追踪：Redis 存储 `agent:{serverId}:heartbeat`（TTL 120 秒）
- [x] Dashboard 显示 Agent 在线/离线状态，离线时标红

### P3-3: Agent 安全
- [x] Agent 注册 API Key 验证：Agent 向 Backend 注册时，Backend 验证 API Key 有效性
- [x] Agent 与 Backend 通信添加 HMAC 签名：请求体 + 时间戳 + Secret 计算 HMAC，防止请求被篡改
- [x] Agent 配置文件中的敏感信息（API Key、Backend URL）加密存储
- [x] Agent 日志脱敏：不记录 API Key、密码等敏感信息

### P3-4: Agent 扩展性
- [x] Agent 添加插件机制：支持用户自定义采集器（Go plugin 或外部命令调用）
- [x] Agent 支持自定义采集路径配置（`/etc/chronovault/agent.yaml` 中配置要监控的配置文件路径）
- [x] Agent 添加 WebSocket 支持：Backend 可通过 WebSocket 实时向 Agent 发送任务（替代当前的 HTTP 轮询）
- [x] Agent 二进制自动更新检查：启动时检查 Backend 是否有新版本，提示用户更新

---

## 🔵 P4 — 测试覆盖

### P4-1: 后端单元测试
- [x] 为 `SnapshotController` 编写 MockMvc 测试（当前 264 个测试主要集中在 Service 层，Controller 层测试不足）
- [x] 为 `ServerController` 编写 MockMvc 测试：验证 CRUD、SSH 连接、密钥轮换
- [x] 为 `AuthController` 编写测试：验证登录、注册、Token 刷新、密码修改
- [x] 为 `AlertController` 编写测试：验证告警列表、确认、规则管理
- [x] 为 `DashboardController` 编写测试：验证 overview 数据聚合
- [x] 为 `StateDiffEngine` 补充边界测试：空 state.json、缺少字段的 state.json、超大 state.json（>1MB）
- [x] 为 `SnapshotEngine` 编写单元测试（Mock SSH 和 Restic）：验证快照创建流程的每个步骤
- [x] 为 `SshConnectionManager` 编写单元测试：验证连接池管理、空闲清理、并发获取
- [x] 为 `CredentialEncryptor` 编写测试：验证加密/解密、错误密钥、空值处理
- [x] 为 `JwtTokenProvider` 编写测试：验证 Token 生成、解析、过期、刷新

### P4-2: 集成测试
- [x] 使用 Testcontainers 搭建集成测试环境（PostgreSQL + Redis），创建 `AbstractIntegrationTest` 基类
- [x] 集成测试：完整快照创建流程（创建服务器 → 创建存储目标 → 触发快照 → 验证数据库状态）
- [x] 集成测试：认证流程（注册 → 登录 → 获取 Token → 访问受保护 API → Token 刷新 → 旧 Token 失效）
- [x] 集成测试：告警触发流程（创建快照 → 模拟高风险变更 → 验证告警生成）
- [x] 集成测试：批量操作（批量删除快照、批量打标签、多服务器批量快照）
- [x] 集成测试验证事务一致性：并发创建快照不会产生脏数据
- [x] 集成测试：WebSocket 连接和消息推送（STOMP over SockJS）

### P4-3: API 兼容性测试
- [x] 创建 `POSTMAN_COLLECTION.json`：包含所有 API 端点的测试集合
- [x] 验证所有 API 的 `Content-Type` 请求/响应一致性
- [x] 验证所有 API 的错误响应格式一致性（遵循 `ApiResponse` 结构）
- [x] 验证分页 API 的边界情况：page=0、page=-1、size=0、size=1000

---

## 🟣 P5 — DevOps 与基础设施

### P5-1: Docker 改进
- [x] `backend/Dockerfile` 验证是否使用多阶段构建（builder 阶段编译 + runtime 阶段运行），确保最终镜像不含 Maven
- [x] `frontend/Dockerfile` 验证多阶段构建（builder 阶段 npm install + build + runtime 阶段 nginx 服务静态文件）
- [x] 为所有 Docker 镜像添加 `HEALTHCHECK` 指令
- [x] `docker-compose.yml` 添加 `restart: unless-stopped` 到所有服务
- [x] 创建 `.dockerignore` 文件（backend 和 frontend 各一份），排除 `.git`、`node_modules`、`target`、`*.md` 等
- [x] Docker Compose 添加 `deploy.resources.limits` 限制每个容器的 CPU 和内存
- [x] 创建 `docker-compose.prod.yml` 覆盖文件：禁用 PostgreSQL/Redis 端口映射（仅内部网络访问）、使用 Named Volume

### P5-2: CI/CD 流水线
- [x] 创建 `.github/workflows/ci.yml`：push/PR 触发，步骤包括 checkout → setup-java → backend test → setup-node → frontend lint → frontend build
- [x] CI 中添加依赖安全扫描步骤（`mvn dependency-check:check`）
- [x] CI 中添加前端依赖审计（`npm audit --production`）
- [x] 创建 `.github/workflows/release.yml`：tag push 触发，构建 Agent 多平台二进制 → 构建 Docker 镜像 → 推送 Docker Hub → 创建 GitHub Release
- [x] CI 缓存 Maven 依赖和 npm 依赖（`actions/cache`），加速构建
- [x] CI 中添加代码质量检查：`mvn checkstyle:check`（或 Spotless）、ESLint
- [x] 为 `agent/` 添加 Go CI：`go test -race ./...` + `go vet ./...` + `golangci-lint`

### P5-3: 监控与可观测性
- [x] `application-prod.yml` 配置 Micrometer + Prometheus metrics 暴露：`/actuator/prometheus`（需认证）
- [x] 创建 Grafana Dashboard JSON：展示快照成功率、备份耗时、SSH 连接质量、活跃告警数等关键指标
- [x] `SnapshotEngine` 添加 Micrometer 自定义指标：`cv_snapshot_duration_seconds`（histogram）、`cv_snapshot_total`（counter，按成功/失败标签）
- [x] `SshConnectionManager` 添加 Micrometer 指标：`cv_ssh_connections_active`（gauge）、`cv_ssh_connections_created_total`（counter）
- [x] `AsyncTaskManager` 添加 Micrometer 指标：`cv_task_active_count`（gauge）、`cv_task_duration_seconds`（histogram）
- [x] 创建 `application-prod.yml` 日志配置：ERROR 级别发送到日志聚合系统（可选配置 webhook URL）
- [x] 创建 `docker-compose.monitoring.yml`：包含 Prometheus + Grafana（可选启动）

### P5-4: 数据库运维
- [x] 创建 Flyway 回滚脚本约定文档（当前 Flyway 社区版不支持 undo，记录手动回滚步骤）
- [x] 为所有新增索引创建性能测试：验证索引在大数据量下的查询性能提升
- [x] 创建数据库备份脚本：`pg_dump chronovault > backup_$(date +%Y%m%d).sql`
- [x] `V12__seed_demo_data.sql` 中的演示数据应在生产环境不执行，添加条件判断或单独的 profile
- [x] 添加数据库连接池监控：HikariCP metrics 暴露到 Actuator（`spring.datasource.hikari.metrics-enabled=true`）

---

## 🟤 P6 — 文档与发布准备

### P6-1: API 文档
- [x] 为所有 Controller 方法添加完整的 `@Operation` 注解（当前部分端点缺失）
- [x] 为所有 DTO 添加 `@Schema` 注解（Swagger 文档中展示字段说明和示例值）
- [x] 创建 `openapi-config.yml`：配置 API 标题、描述、版本、联系信息、License
- [x] Swagger UI 添加认证支持（Bearer Token 输入框），方便调试需要认证的 API

### P6-2: 开发者文档
- [x] 更新 `CLAUDE.md`：添加最新的数据库表结构说明、新增 API 端点列表、测试运行命令
- [x] 创建 `docs/ARCHITECTURE.md`：详细架构图、模块职责说明、数据流图、部署拓扑图
- [x] 创建 `docs/CONTRIBUTING.md` 更新：编码规范、提交规范、分支策略、Code Review 流程
- [x] 创建 `docs/API_REFERENCE.md`：从 OpenAPI spec 生成的完整 API 文档
- [x] 创建 `docs/DEPLOYMENT.md`：从零部署指南（Linux/CentOS/Ubuntu），包含系统要求、依赖安装、配置说明
- [x] `README.md` 添加 badges：CI 状态、测试覆盖率、License、版本号

### P6-3: 用户文档
- [x] 创建 `docs/QUICKSTART.md`：5 分钟快速开始（Docker Compose 一键启动 + 登录 + 添加服务器 + 第一次快照）
- [x] 创建 `docs/USER_GUIDE.md`：用户操作手册（截图 + 步骤说明）
- [x] 创建 `docs/AGENT_INSTALLATION.md`：Agent 安装详细指南（支持 Ubuntu/CentOS/Debian/Alpine）
- [x] 创建 `docs/TROUBLESHOOTING.md`：常见问题解答（SSH 连接失败、备份失败、空间不足等）
- [x] 创建 `docs/SECURITY.md` 更新：安全架构说明、威胁模型、已知安全约束

### P6-4: 发布准备
- [x] `CHANGELOG.md` 添加 Unreleased 区域，持续记录变更
- [x] 创建 `.github/ISSUE_TEMPLATE/bug_report.md` 和 `feature_request.md`
- [x] 创建 `.github/PULL_REQUEST_TEMPLATE.md`
- [x] 添加 GitHub Topics：`backup`, `server-management`, `devops`, `self-hosted`, `restic`, `go-agent`, `vue3`, `spring-boot`, `state-management`
- [x] 打 v0.1.0 标签，创建 GitHub Release（附带 Agent 多平台二进制 + Release Notes）

---

## ⚪ P7 — 差异化核心功能

### P7-1: Git 操作完善
- [x] `ServerBranchController` 验证 Branch 创建/切换/合并功能是否真正工作（Branch 实体和操作需要连接到实际快照链）
- [x] `ServerStashController` 验证 Stash 创建/Pop/Discard 是否完整实现
- [x] `ChangeAttributionController`（Blame）验证：查看某个配置文件在哪些快照中被修改，由谁修改
- [x] Bisect 功能端到端验证：创建多个快照 → 启动 bisect → 标记 good/bad → 验证最终定位结果
- [x] Cherry-pick 功能验证：从一个服务器的快照中提取特定变更应用到另一台服务器
- [x] 前端 Branch/Stash/Blame/Bisect 视图完善：确认每个 Git 风格操作都有对应的 UI 入口和操作流程

### P7-2: 多服务器管理
- [x] `ServerGroupController` 验证：服务器分组（prod/staging/dev）是否正确工作
- [x] 批量快照功能验证：选择多台服务器 → 一键创建快照 → Dashboard 展示进度
- [x] `StorageReplicationService` 验证：快照跨存储复制是否完整实现
- [x] Dashboard 拓扑视图（`TopologyDTO`）验证：展示服务器之间的关系和状态
- [x] `DriftDetectionController` 验证：漂移检测功能是否能对比当前状态和上次快照状态

### P7-3: 告警与通知
- [x] `AlertController` 和 `AlertRuleManager` 验证：告警规则是否支持自定义阈值和条件
- [x] `NotificationService` 验证：Webhook 推送（Slack/DingTalk/自定义 URL）是否完整实现
- [x] `WebhookController` 验证：Webhook 配置、测试、重发功能是否完整
- [x] 创建告警聚合和降噪逻辑：相同告警在 5 分钟内不重复发送
- [x] 告警升级：高危告警（SSH 断连、磁盘满）自动发送邮件 + Webhook

### P7-4: 灾难恢复
- [x] `DisasterRecoveryPlanController` 验证：灾难恢复计划的创建、编辑、执行功能
- [x] 灾难恢复演练功能：模拟服务器故障 → 执行恢复计划 → 验证恢复结果
- [x] 恢复计划中支持执行自定义脚本（通过 SSH 在目标服务器上执行）
- [x] 创建 `DisasterRecoveryPlaybook` 模板：预置常见场景的恢复步骤（Web 服务器、数据库服务器、缓存服务器）

### P7-5: AI 增强（MiMo 集成）
- [x] `AiClient` 验证：调用 MiMo API 是否正常工作，添加请求/响应日志（脱敏后）
- [x] `AiAnalysisService` 验证：快照智能分析（识别异常包升级、风险配置变更、优化建议）
- [ ] 前端 `AiInsights.vue` 验证：AI 洞察是否正确展示和交互
- [ ] AI 推荐引擎：基于历史快照模式，自动推荐备份策略（频率、保留策略、路径选择）
- [ ] AI 异常检测：对比当前状态和历史基线，自动标记异常（异常端口开放、异常进程启动等）

---

## 🏁 P8 — 超越竞品的差异化特性

### P8-1: Serverless 状态采集
- [ ] Agent 添加被动采集模式：文件变更时自动触发 state.json 增量更新（inotify/fswatch 监控 /etc 目录）
- [x] Backend 添加"实时状态"API：不创建快照，仅获取服务器当前实时状态（用于与最近快照对比）

### P8-2: 可视化增强
- [x] 创建"快照影响分析"视图：展示某个快照影响了文件、服务、配置（后端API已实现）
- [x] 创建"服务器健康趋势"图表：过去 7/30 天的快照频率、告警趋势、变更频率
- [x] 创建"变更热力图"：展示一周内每天的变更数量（类似 GitHub 贡献图）
- [ ] Dashboard 添加实时数据流：WebSocket 推送新的快照状态、告警、任务进度

### P8-3: 自动化运维
- [ ] 创建"智能快照"功能：基于变更频率自动调整快照频率（高频变更期间增加快照，空闲期减少快照）
- [ ] 创建"配置漂移自动修复"：检测到非授权配置变更后，自动恢复到上一个已知良好状态
- [ ] 创建"快照合规检查"：根据 RetentionPolicy 自动清理不合规的快照，生成合规报告
- [ ] 创建"一键环境复制"：从生产服务器快照自动创建测试/预发环境的完整副本

### P8-4: 多租户与团队协作
- [x] 验证现有 RBAC（OWNER/ADMIN/MEMBER/VIEWER）是否在所有 API 端点上正确执行
- [x] `TeamController` 验证：成员邀请、角色变更、权限管理是否完整
- [x] 创建"操作审计看板"：谁在什么时候对哪台服务器做了什么操作（时间线视图）
- [x] 添加"共享快照"功能：团队成员之间可以分享快照视图（只读链接）

### P8-5: 生态系统
- [x] 创建 REST API v1 完整文档和 Postman Collection，方便第三方集成
- [x] 创建 CLI 工具（Go）：`chronovault-cli`，支持从命令行管理快照（`cv snapshot list/create/rollback/diff`）
- [ ] 创建 Terraform Provider：通过 IaC 方式管理 ChronoVault 的服务器注册和快照策略
- [x] 创建 Webhook 集成模板：Slack、DingTalk、Feishu、企业微信的消息格式模板
- [x] 支持 Prometheus metrics 端点：让现有监控栈（Prometheus + Grafana）可直接对接

---

## 完成信号

当以下所有条件满足时，输出 EXIT_SIGNAL：

```
RALPH_STATUS:
  STATUS: COMPLETE
  EXIT_SIGNAL: true
  REASON: All P0-P3 tasks verified, integration tests green, security audit passed
```

在此之前，每次循环结束时输出：

```
RALPH_STATUS:
  STATUS: IN_PROGRESS
  EXIT_SIGNAL: false
  COMPLETED_THIS_LOOP: [具体完成的任务编号和简述]
  NEXT_LOOP_FOCUS: [下一轮优先做的任务]
  BLOCKERS: [阻塞问题，如果有]
  METRICS: tests_passing=[数字] / api_endpoints=[数字] / coverage=[百分比]
```
