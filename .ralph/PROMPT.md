# ChronoVault — Ralph Autonomous Development Instructions

## 项目使命

你正在构建 **ChronoVault**：一个面向中小团队的**服务器状态版本控制平台**。

核心价值主张：**让没有 DevOps 经验的开发者，像用 Git 管代码一样管理服务器环境，一键回到任何历史状态。**

竞品参照：[Backrest](https://github.com/garethgeorge/backrest)（5.9k stars，Go，单机备份）。
ChronoVault 的差异化：**多服务器集中管理 + 状态感知快照（不只是文件，还有包、服务、配置、Docker 状态的版本化）+ Git 风格 Diff 界面**。

---

## 你的工作原则

1. **永远优先让代码真正跑通**，而不是让代码看起来漂亮。每完成一个模块，必须有可验证的测试或运行日志。
2. **不要重写已有的工作代码**。项目已有 Spring Boot 后端、Vue 3 前端、Go Agent 骨架。在现有代码基础上修复和补全，不要推倒重来。
3. **遇到选择时选简单的**。能用标准库解决的不引入新依赖。能用现有 Restic 解决的不自己造轮子。
4. **每完成一个任务立即 commit**，commit message 遵循 `feat:` `fix:` `test:` `refactor:` 前缀约定。
5. **安全红线**：SSH 密钥、密码、JWT secret 全部走 AES-256-GCM 加密存储，绝不明文落库或日志。
6. **测试覆盖红线**：核心业务路径（快照创建、回滚执行、Agent 通信）必须有单元测试或集成测试。
7. 遇到无法确定的设计决策，选择与 Backrest 或 Proxmox Backup Server 的成熟方案对齐。

---

## 技术栈（不可随意更换）

| 组件 | 技术 | 说明 |
|------|------|------|
| Backend | Spring Boot 3.2.5 / Java 17 | 主服务，REST API |
| Frontend | Vue 3.5 + TypeScript + Vite + Tailwind CSS 4 | SPA |
| Agent | Go 1.22 | 部署在目标服务器的守护进程 |
| 数据库 | PostgreSQL 15 | 主存储 |
| 缓存/队列 | Redis 7 | 任务队列、WebSocket pub/sub |
| 备份引擎 | Restic CLI | 文件级快照，不要自己实现 |
| 容器化 | Docker + Docker Compose | 开发和部署 |
| 迁移 | Flyway | 数据库版本管理 |

---

## 架构理解

```
用户浏览器
    │ HTTPS / WebSocket
    ▼
Frontend (Vue 3) :80
    │ REST API / WebSocket
    ▼
Backend (Spring Boot) :8080
    │ HTTP (Bearer Token)          │ PostgreSQL :5432
    ▼                              │ Redis :6379
Agent (Go) :8081                   │
    │                              ▼
    ├── 调用 Restic CLI         Backend DB
    ├── 采集 state.json
    │   ├── 已安装包列表
    │   ├── systemd 服务状态
    │   ├── 开放端口
    │   ├── Docker 容器/镜像
    │   ├── /etc 配置文件 hash
    │   └── crontab
    └── 报告给 Backend
```

**关键概念**：
- **Snapshot** = Restic 文件备份 + state.json（系统状态快照）的组合体
- **state.json** = ChronoVault 的核心差异化，这是 Backrest 没有的东西
- **Diff** = 两个 Snapshot 的 state.json 之间的对比，展示"这次快照和上次相比变了什么"
- **Rollback** = 文件恢复（Restic restore）+ 状态恢复（包/服务/配置选择性回滚）

---

## 当前项目状态评估

### 已有但需要验证/修复的
- Backend：17 个 Controller、15 个 Service、22 个 Flyway 迁移 ✅ 骨架存在
- Frontend：14 个视图、14 个 API 模块、TypeScript 类型定义 ✅ 骨架存在
- Agent：scanner、transport、restic 封装 ⚠️ 核心逻辑需要验证
- Docker Compose：存在 ✅

### 必须从零实现的（差异化核心）
- `state.json` 采集器（Agent 端）
- Snapshot Diff 引擎（Backend 端）
- Git 风格时间线 + Diff UI（Frontend 端）
- 选择性回滚逻辑（Backend + Agent）

### 已知缺陷（必须修复）
- Agent `executeTask()` 的 SNAPSHOT/RECOVER 逻辑是否真正调用 Restic
- 前端 `any` 类型泛滥
- `DashboardService.getActivityTrend()` 全量加载问题
- SSH TOFU 安全问题（生产环境需要 known_hosts 验证）

---

## 成功标准

**阶段 P0（地基，必须先完成）**
- [ ] `docker-compose up -d` 能完整启动所有服务，无报错
- [ ] 后端 `/actuator/health` 返回 UP
- [ ] 前端能正常加载，无 console.error
- [ ] 能通过 UI 添加一台服务器（SSH 连接成功）
- [ ] Agent 能在目标服务器上安装并成功注册到 Backend

**阶段 P1（核心功能）**
- [ ] 能创建一个真实快照（Restic 备份 + state.json 采集）
- [ ] 快照详情页显示 state.json 内容（包列表、服务状态、Docker 容器）
- [ ] 两个快照之间能生成 Diff（至少包列表和服务状态的变更）
- [ ] 能执行一次完整的文件级回滚并验证成功

**阶段 P2（差异化特性）**
- [ ] Git 风格时间线视图（带 commit message 和变更摘要）
- [ ] 状态 Diff 可视化（语法高亮的配置文件 diff，类似 GitHub diff 视图）
- [ ] 选择性回滚（只回滚某个配置文件或某个包版本）
- [ ] 变更告警（state.json 检测到预期外变更时通知）

**阶段 P3（生产就绪）**
- [ ] 端到端集成测试覆盖核心链路
- [ ] 性能：快照列表加载 < 500ms（1000 条数据）
- [ ] 安全审计：无已知 CVE 依赖，SSH 密钥加密存储验证
- [ ] 文档：README 有真实环境安装截图/录屏
- [ ] 压测：并发 10 个快照任务不崩溃

---

## 文件结构参考

```
chronovault/
├── backend/src/main/java/com/chronovault/
│   ├── controller/          # REST 控制器
│   ├── service/             # 业务逻辑
│   ├── entity/              # JPA 实体（Snapshot, Server, StateSnapshot, SnapshotDiff）
│   ├── repository/          # Spring Data 仓库
│   ├── dto/                 # 请求/响应 DTO
│   ├── ssh/                 # SSH 连接池（Apache MINA SSHD）
│   ├── snapshot/            # 快照引擎（Restic CLI 调用）
│   ├── diff/                # Diff 引擎（state.json 比较逻辑）← 新增
│   ├── storage/             # 多存储后端（S3/OSS/Local）
│   ├── ai/                  # AI 分析（MiMo/OpenAI）
│   └── security/            # JWT + AES-256-GCM
├── frontend/src/
│   ├── views/
│   │   ├── Timeline.vue     # Git 风格时间线 ← 核心新增
│   │   ├── SnapshotDiff.vue # Diff 视图 ← 核心新增
│   │   └── ...              # 已有视图
│   ├── components/
│   │   ├── DiffViewer.vue   # 语法高亮 diff 组件 ← 新增
│   │   └── StateTree.vue    # 状态树展示组件 ← 新增
│   └── ...
├── agent/
│   ├── scanner/
│   │   ├── state.go         # state.json 采集器 ← 核心新增
│   │   ├── packages.go      # 包列表采集 ← 新增
│   │   ├── services.go      # systemd 服务采集 ← 新增
│   │   ├── docker.go        # Docker 状态采集 ← 新增
│   │   └── configs.go       # /etc 配置 hash 采集 ← 新增
│   ├── restic/
│   │   └── client.go        # Restic CLI 封装 ← 需要完善
│   └── ...
└── docker-compose.yml
```

---

## 关键接口约定

### state.json 格式（Agent 采集，存入 Snapshot）
```json
{
  "collected_at": "2026-06-03T10:00:00Z",
  "agent_version": "0.1.0",
  "os": {
    "name": "Ubuntu",
    "version": "22.04",
    "kernel": "5.15.0-91-generic",
    "arch": "x86_64"
  },
  "packages": [
    {"name": "nginx", "version": "1.24.0", "manager": "apt"}
  ],
  "services": [
    {"name": "nginx", "status": "active", "enabled": true, "pid": 1234}
  ],
  "ports": [
    {"port": 80, "protocol": "tcp", "process": "nginx", "state": "LISTEN"}
  ],
  "docker": {
    "containers": [
      {"id": "abc123", "name": "my-app", "image": "node:18", "status": "running", "ports": ["3000:3000"]}
    ],
    "compose_files": ["/opt/app/docker-compose.yml"]
  },
  "configs": [
    {"path": "/etc/nginx/nginx.conf", "sha256": "abcd1234...", "size": 2048}
  ],
  "crontab": [
    {"user": "root", "schedule": "0 2 * * *", "command": "/opt/backup.sh"}
  ]
}
```

### Diff 响应格式（Backend 计算，Frontend 展示）
```json
{
  "snapshot_a": "snap_id_1",
  "snapshot_b": "snap_id_2",
  "summary": {
    "packages_added": 2,
    "packages_removed": 0,
    "packages_upgraded": 3,
    "services_changed": 1,
    "ports_changed": 0,
    "configs_changed": 2
  },
  "packages": {
    "added": [{"name": "curl", "version": "7.88.1"}],
    "removed": [],
    "upgraded": [{"name": "nginx", "from": "1.22.0", "to": "1.24.0"}]
  },
  "services": {
    "changed": [{"name": "nginx", "from": {"status": "inactive"}, "to": {"status": "active"}}]
  },
  "configs": {
    "changed": [
      {
        "path": "/etc/nginx/nginx.conf",
        "diff": "--- a/etc/nginx/nginx.conf\n+++ b/etc/nginx/nginx.conf\n@@ -10,7 +10,7 @@\n-    worker_processes 1;\n+    worker_processes 4;"
      }
    ]
  }
}
```

---

## 完成信号

当以下所有条件满足时，输出 EXIT_SIGNAL：

```
RALPH_STATUS:
  STATUS: COMPLETE
  EXIT_SIGNAL: true
  REASON: All P0+P1+P2 tasks verified passing, integration tests green, docker-compose up clean
```

在此之前，每次循环结束时输出：

```
RALPH_STATUS:
  STATUS: IN_PROGRESS
  EXIT_SIGNAL: false
  COMPLETED_THIS_LOOP: [具体完成的任务]
  NEXT_LOOP_FOCUS: [下一轮要做的事]
  BLOCKERS: [如果有阻塞问题]
```
