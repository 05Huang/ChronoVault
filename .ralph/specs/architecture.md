# ChronoVault 技术架构规范

## 核心数据模型

### Server（目标服务器）
```java
@Entity
public class Server {
    UUID id;
    String name;
    String host;          // IP 或域名
    Integer sshPort;      // 默认 22
    String sshUser;
    String encryptedSshKey;   // AES-256-GCM 加密
    String encryptedSshPassword; // AES-256-GCM 加密
    String agentToken;         // Agent 认证 token（加密存储）
    String agentUrl;           // Agent HTTP URL
    ServerStatus status;       // ONLINE / OFFLINE / UNKNOWN
    Instant lastSeenAt;
    Instant createdAt;
    Team team;
}
```

### Snapshot（快照）
```java
@Entity
public class Snapshot {
    UUID id;
    String resticSnapshotId;   // Restic 快照 ID
    String message;            // 用户设置的 commit message（可为空）
    SnapshotStatus status;     // PENDING / RUNNING / SUCCESS / FAILED
    String resticRepo;         // Restic 仓库路径/URL
    Long backupSizeBytes;
    Long deduplicatedSizeBytes;
    
    // state.json 数据
    @Column(columnDefinition = "jsonb")
    String stateJson;
    Instant stateCollectedAt;
    
    // 变更摘要（冗余存储，避免每次 diff 计算）
    @Column(columnDefinition = "jsonb")
    String changeSummaryJson;  // 相对于上一个快照的摘要
    
    UUID previousSnapshotId;   // 上一个快照的 ID（链表结构）
    
    Server server;
    User createdBy;
    Instant startedAt;
    Instant completedAt;
    Instant createdAt;
}
```

### SnapshotDiff（按需计算，缓存）
```java
@Entity
public class SnapshotDiff {
    UUID id;
    UUID snapshotAId;
    UUID snapshotBId;
    
    @Column(columnDefinition = "jsonb")
    String diffJson;   // 完整 diff 结果，格式见 PROMPT.md
    
    Instant computedAt;
    
    // 唯一索引 (snapshot_a_id, snapshot_b_id)
}
```

### AlertRule（告警规则）
```java
@Entity
public class AlertRule {
    UUID id;
    String name;
    AlertRuleType type;   // NEW_PORT_OPENED / SERVICE_DISABLED / CRITICAL_CONFIG_CHANGED / IMAGE_DOWNGRADED
    String pattern;       // 匹配模式（如端口号范围、配置文件路径 glob）
    AlertSeverity severity;  // HIGH / MEDIUM / LOW
    Server server;           // null = 适用所有服务器
    boolean enabled;
}
```

---

## Agent 通信协议

### Backend → Agent（任务下发）
```
POST /api/agent/tasks
Authorization: Bearer {agent_token}

{
  "task_id": "uuid",
  "type": "SNAPSHOT | RESTORE | SCAN | HEALTH_CHECK",
  "payload": {
    // SNAPSHOT:
    "restic_repo": "s3:bucket/repo",
    "restic_password": "encrypted_value",
    "include_paths": ["/opt/app", "/etc/nginx"],
    "exclude_patterns": ["*.log", "node_modules", ".git"],
    "collect_state": true
    
    // RESTORE:
    "restic_snapshot_id": "abc123",
    "target_path": "/",
    "restore_mode": "FULL | SELECTIVE",
    "selective_items": [...]
    
    // SCAN:
    "scan_depth": "BASIC | FULL"
  }
}
```

### Agent → Backend（结果上报）
```
POST /api/agent/results
Authorization: Bearer {agent_token}

{
  "task_id": "uuid",
  "status": "SUCCESS | FAILED | IN_PROGRESS",
  "progress_percent": 75,
  "message": "Backing up /opt/app... (1.2GB / 1.6GB)",
  "result": {
    // SNAPSHOT 完成时:
    "restic_snapshot_id": "def456",
    "backup_size_bytes": 1073741824,
    "deduplicated_size_bytes": 52428800,
    "state_json": { ... }  // 完整的 StateSnapshot 对象
  },
  "error": null  // 或错误详情
}
```

---

## 安全规范

### 密钥加密
- 所有敏感字段（SSH 密钥、密码、Agent token）使用 AES-256-GCM 加密
- 加密密钥来自环境变量 `CHRONOVAULT_MASTER_KEY`（生产必须设置）
- 加密格式：`{iv_base64}:{ciphertext_base64}:{tag_base64}`
- 实现：`security/CryptoService.java`

### SSH 连接安全
- 生产环境：`chronovault.ssh.strict-host-checking=true`
- 首次连接展示指纹，用户确认后存入 `ServerHostKey` 表（加密存储）
- 后续连接验证指纹，不匹配则拒绝并告警
- 开发环境：可以设置 `chronovault.ssh.strict-host-checking=false`（不推荐生产使用）

### API 安全
- JWT 有效期：access token 1 小时，refresh token 7 天
- 速率限制：`/api/auth/*` 接口 30 次/分钟（滑动窗口，按 IP+路径）
- Agent API：使用独立的 Bearer token，与用户 JWT 隔离
- 所有涉及服务器操作的 API 需要验证用户对该服务器有权限

---

## 性能要求

| 接口 | P99 延迟 | 并发 |
|------|---------|------|
| GET /api/snapshots（1000 条） | < 200ms | 50 |
| GET /api/snapshots/diff | < 2s | 20 |
| GET /api/dashboard/overview | < 500ms | 100 |
| POST /api/snapshots（创建） | 异步，立即返回 task_id | - |

### 数据库索引
```sql
-- 快照列表查询
CREATE INDEX idx_snapshots_server_created ON snapshots(server_id, created_at DESC);

-- state_json JSONB 查询
CREATE INDEX idx_snapshots_state ON snapshots USING gin(state_json);

-- 告警查询
CREATE INDEX idx_alerts_server_created ON alerts(server_id, created_at DESC) WHERE resolved_at IS NULL;
```

---

## 错误处理规范

### API 错误响应格式
```json
{
  "error": {
    "code": "SNAPSHOT_AGENT_UNREACHABLE",
    "message": "Cannot connect to agent on server 'prod-01'. Agent may be offline.",
    "details": {
      "server_id": "uuid",
      "agent_url": "http://10.0.0.1:8081",
      "last_seen_at": "2026-06-03T09:00:00Z"
    }
  }
}
```

### 错误码规范
- `AUTH_*`：认证/授权错误
- `SERVER_*`：服务器管理错误
- `SNAPSHOT_*`：快照相关错误
- `AGENT_*`：Agent 通信错误
- `STORAGE_*`：存储错误
- `VALIDATION_*`：请求参数错误

---

## 前端组件规范

### 颜色语义
- 新增/成功：`text-green-500` / `bg-green-50`
- 删除/危险：`text-red-500` / `bg-red-50`
- 修改/警告：`text-yellow-500` / `bg-yellow-50`
- 信息/中性：`text-blue-500` / `bg-blue-50`

### Diff 视图颜色（对标 GitHub）
- 新增行：`bg-green-50` + 左边框 `border-l-4 border-green-500`
- 删除行：`bg-red-50` + 左边框 `border-l-4 border-red-500`
- 上下文行：`bg-white`
- 行号：`text-gray-400 text-xs`

### 快照时间线 commit 格式
```
● abc1234  [2026-06-03 10:23]  feat: 升级 nginx 到 1.24
  +2 pkgs  ~1 cfg  ⚠ port 8080 opened
  
● def5678  [2026-06-02 18:00]  手动快照
  (no changes)
  
● ghi9012  [2026-06-01 02:00]  auto: scheduled backup
  +0 pkgs  +1 svc
```
