# ChronoVault API Reference

> Base URL: `http://localhost:8080/api/v1`
> 
> All endpoints require JWT Bearer Token authentication unless noted otherwise.

---

## Table of Contents

- [Authentication](#authentication)
- [Error Handling](#error-handling)
- [Pagination](#pagination)
- [API Endpoints](#api-endpoints)
  - [Auth](#auth)
  - [Servers](#servers)
  - [Snapshots](#snapshots)
  - [Storage](#storage)
  - [Alerts](#alerts)
  - [Dashboard](#dashboard)
  - [Tasks](#tasks)
  - [Agent](#agent)
  - [AI](#ai)
  - [Recovery](#recovery)
  - [Risk](#risk)
  - [Scheduled Backups](#scheduled-backups)
  - [Retention Policies](#retention-policies)
  - [Server Groups](#server-groups)
  - [Server Branches](#server-branches)
  - [Server Stash](#server-stash)
  - [Snapshots Tags](#snapshot-tags)
  - [Snapshot Hooks](#snapshot-hooks)
  - [Settings](#settings)
  - [Team](#team)
  - [Terminal](#terminal)
  - [Files](#files)
  - [Webhooks](#webhooks)
  - [Integrations](#integrations)
  - [Verification Jobs](#verification-jobs)
  - [Disaster Recovery](#disaster-recovery)
  - [Change Attribution](#change-attribution)
  - [Drift Detection](#drift-detection)

---

## Authentication

### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 1,
      "name": "Admin",
      "email": "user@example.com",
      "role": "OWNER"
    }
  }
}
```

### Register

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "name": "New User",
  "email": "new@example.com",
  "password": "password123"
}
```

### Refresh Token

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### Get Current User

```http
GET /api/v1/auth/me
Authorization: Bearer <token>
```

### Change Password

```http
PUT /api/v1/auth/password
Authorization: Bearer <token>
Content-Type: application/json

{
  "oldPassword": "oldpass",
  "newPassword": "newpass"
}
```

### Update Profile

```http
PUT /api/v1/auth/profile
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Updated Name"
}
```

---

## Error Handling

### Error Response Format

```json
{
  "code": 40001,
  "message": "快照标题不能为空",
  "data": null
}
```

### Common Error Codes

| Code | Description |
|------|-------------|
| 40001 | Bad Request - 参数验证失败 |
| 40101 | Unauthorized - 认证失败 |
| 40301 | Forbidden - 权限不足 |
| 40401 | Not Found - 资源不存在 |
| 40901 | Conflict - 资源冲突 |
| 50001 | Internal Server Error - 服务器内部错误 |

---

## Pagination

### Request Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | 页码（从 0 开始） |
| `size` | int | 20 | 每页数量（最大 100） |
| `sort` | string | - | 排序字段 |
| `direction` | string | DESC | 排序方向（ASC/DESC） |

### Response Format

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "content": [...],
    "totalElements": 150,
    "totalPages": 8,
    "size": 20,
    "number": 0
  }
}
```

---

## API Endpoints

### Auth

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/login` | 用户登录 |
| POST | `/api/v1/auth/register` | 用户注册 |
| GET | `/api/v1/auth/me` | 获取当前用户信息 |
| PUT | `/api/v1/auth/password` | 修改密码 |
| PUT | `/api/v1/auth/profile` | 更新个人资料 |
| POST | `/api/v1/auth/refresh` | 刷新 Token |

---

### Servers

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/servers` | 获取服务器列表（分页） |
| GET | `/api/v1/servers/{id}` | 获取服务器详情 |
| POST | `/api/v1/servers` | 创建服务器 |
| POST | `/api/v1/servers/clone` | 克隆服务器配置 |
| DELETE | `/api/v1/servers/{id}` | 删除服务器 |
| PUT | `/api/v1/servers/{id}/auto-snapshot` | 切换自动快照 |
| POST | `/api/v1/servers/{id}/connect` | 测试 SSH 连接 |
| PUT | `/api/v1/servers/{id}/ssh` | 更新 SSH 配置 |
| POST | `/api/v1/servers/{id}/test-connection` | 测试连接 |
| GET | `/api/v1/servers/{id}/health` | 获取服务器健康状态 |
| POST | `/api/v1/servers/{id}/health/refresh` | 刷新健康状态 |
| GET | `/api/v1/servers/{id}/live-state` | 获取实时状态 |
| POST | `/api/v1/servers/{id}/rotate-key` | 轮换 SSH 密钥 |
| POST | `/api/v1/servers/{id}/install-agent` | 安装 Agent |
| POST | `/api/v1/servers/{id}/scan-environment` | 扫描环境 |
| POST | `/api/v1/servers/{id}/ai-analyze` | AI 分析服务器 |
| POST | `/api/v1/servers/batch-scan` | 批量扫描 |
| GET | `/api/v1/servers/{id}/containers` | 获取容器列表 |
| POST | `/api/v1/servers/{id}/containers/create` | 创建容器 |
| POST | `/api/v1/servers/{id}/containers/{cid}/start` | 启动容器 |
| POST | `/api/v1/servers/{id}/containers/{cid}/stop` | 停止容器 |
| POST | `/api/v1/servers/{id}/containers/{cid}/restart` | 重启容器 |
| DELETE | `/api/v1/servers/{id}/containers/{cid}` | 删除容器 |
| GET | `/api/v1/servers/{id}/volumes` | 获取卷列表 |
| POST | `/api/v1/servers/{id}/volumes` | 添加卷 |
| GET | `/api/v1/servers/{id}/logs` | 获取日志 |
| DELETE | `/api/v1/servers/{id}/logs` | 清除日志 |
| GET | `/api/v1/servers/{id}/images` | 获取镜像列表 |
| POST | `/api/v1/servers/{id}/images/pull` | 拉取镜像 |
| DELETE | `/api/v1/servers/{id}/images/{imageId}` | 删除镜像 |
| GET | `/api/v1/servers/{id}/networks` | 获取网络列表 |
| GET | `/api/v1/servers/{id}/topology` | 获取服务器拓扑 |

---

### Snapshots

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/snapshots` | 获取快照列表（分页） |
| GET | `/api/v1/snapshots/all` | 获取所有快照（不分页） |
| GET | `/api/v1/snapshots/{id}` | 获取快照详情 |
| POST | `/api/v1/snapshots` | 创建快照 |
| DELETE | `/api/v1/snapshots/{id}` | 删除快照 |
| GET | `/api/v1/snapshots/{id}/diff` | 获取快照差异 |
| GET | `/api/v1/snapshots/compare` | 对比两个快照 |
| GET | `/api/v1/snapshots/{id}/state` | 获取快照状态 |
| GET | `/api/v1/snapshots/{id}/summary` | 获取变更摘要 |
| GET | `/api/v1/snapshots/state-diff` | 获取状态差异 |
| GET | `/api/v1/snapshots/timeline` | 获取时间线 |
| POST | `/api/v1/snapshots/{id}/rollback` | 回滚到快照 |
| GET | `/api/v1/snapshots/{id}/rollback/preview` | 预览回滚 |
| POST | `/api/v1/snapshots/{id}/rollback/selective` | 选择性回滚 |
| POST | `/api/v1/snapshots/{id}/revert` | 恢复文件 |
| POST | `/api/v1/snapshots/{id}/cherry-pick` | Cherry-pick 变更 |
| GET | `/api/v1/snapshots/{id}/files` | 获取快照文件列表 |
| GET | `/api/v1/snapshots/{id}/files/download` | 下载快照文件 |
| POST | `/api/v1/snapshots/{id}/verify` | 验证快照 |
| GET | `/api/v1/snapshots/{id}/containers` | 获取快照容器状态 |
| GET | `/api/v1/snapshots/{id}/containers/compare` | 对比容器状态 |
| POST | `/api/v1/snapshots/{id}/replicate` | 复制快照 |
| POST | `/api/v1/snapshots/{id}/restore-files` | 恢复文件 |
| POST | `/api/v1/snapshots/batch-tag` | 批量打标签 |
| POST | `/api/v1/snapshots/batch-delete` | 批量删除 |
| POST | `/api/v1/snapshots/batch` | 批量创建快照 |
| GET | `/api/v1/snapshots/batch/{batchId}` | 获取批量任务状态 |
| POST | `/api/v1/snapshots/cleanup` | 清理过期快照 |
| GET | `/api/v1/snapshots/export` | 导出快照 |
| GET | `/api/v1/snapshots/export/{taskId}/download` | 下载导出文件 |
| GET | `/api/v1/snapshots/{id}/impact` | 获取快照影响分析 |
| POST | `/api/v1/snapshots/bisect/start` | 启动 Bisect |
| POST | `/api/v1/snapshots/bisect/{sessionId}/mark` | 标记 Bisect 结果 |
| GET | `/api/v1/snapshots/bisect/{sessionId}` | 获取 Bisect 状态 |

---

### Snapshot Tags

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/snapshots/{snapshotId}/tags` | 获取快照标签 |
| POST | `/api/v1/snapshots/{snapshotId}/tags` | 创建标签 |
| DELETE | `/api/v1/snapshots/{snapshotId}/tags/{tagName}` | 删除标签 |

---

### Snapshot Hooks

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/servers/{serverId}/hooks` | 获取钩子列表 |
| POST | `/api/v1/servers/{serverId}/hooks` | 创建钩子 |
| PUT | `/api/v1/servers/{serverId}/hooks/{hookId}` | 更新钩子 |
| DELETE | `/api/v1/servers/{serverId}/hooks/{hookId}` | 删除钩子 |

---

### Server Branches (Git-style)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/servers/{serverId}/branches` | 获取分支列表 |
| POST | `/api/v1/servers/{serverId}/branches` | 创建分支 |
| DELETE | `/api/v1/servers/{serverId}/branches/{branchId}` | 删除分支 |
| PUT | `/api/v1/servers/{serverId}/branches/{branchId}` | 重命名分支 |
| POST | `/api/v1/servers/{serverId}/branches/{branchId}/switch` | 切换分支 |
| POST | `/api/v1/servers/{serverId}/branches/merge` | 合并分支 |

---

### Server Stash

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/servers/{serverId}/stash` | 创建 Stash |
| GET | `/api/v1/servers/{serverId}/stash` | 获取 Stash 列表 |
| POST | `/api/v1/servers/{serverId}/stash/pop` | Pop Stash |
| DELETE | `/api/v1/servers/{serverId}/stash/{stashId}` | 删除 Stash |

---

### Storage

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/storage/overview` | 获取存储概览 |
| GET | `/api/v1/storage/distribution` | 获取存储分布 |
| GET | `/api/v1/storage/health` | 获取存储健康状态 |
| POST | `/api/v1/storage` | 创建存储目标 |
| DELETE | `/api/v1/storage/{id}` | 删除存储目标 |

---

### Alerts

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/alerts` | 获取告警列表（分页） |
| GET | `/api/v1/alerts/stats` | 获取告警统计 |
| POST | `/api/v1/alerts/{id}/restart` | 重启服务 |
| POST | `/api/v1/alerts/{id}/expand-storage` | 扩展存储 |
| POST | `/api/v1/alerts/{id}/rollback-config` | 回滚配置 |
| POST | `/api/v1/alerts/{id}/dismiss` | 关闭告警 |
| GET | `/api/v1/alerts/rules` | 获取告警规则 |
| POST | `/api/v1/alerts/rules` | 创建告警规则 |
| DELETE | `/api/v1/alerts/rules/{id}` | 删除告警规则 |

---

### Dashboard

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/dashboard/overview` | 获取仪表盘概览 |
| GET | `/api/v1/dashboard/stats` | 获取统计数据 |
| GET | `/api/v1/dashboard/anomalies` | 获取异常列表 |
| GET | `/api/v1/dashboard/storage-summary` | 获取存储摘要 |
| GET | `/api/v1/dashboard/risk-score` | 获取风险评分 |
| GET | `/api/v1/dashboard/topology` | 获取拓扑图 |
| GET | `/api/v1/dashboard/activity-trend` | 获取活动趋势 |

---

### Tasks

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/tasks` | 获取任务列表 |
| GET | `/api/v1/tasks/{id}` | 获取任务详情 |
| POST | `/api/v1/tasks/{id}/cancel` | 取消任务 |

---

### Agent

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/agent/register` | Agent 注册 |
| POST | `/api/v1/agent/heartbeat` | Agent 心跳 |
| POST | `/api/v1/agent/tasks/pending` | 获取待处理任务 |
| POST | `/api/v1/agent/tasks/{taskId}/progress` | 上报任务进度 |
| POST | `/api/v1/agent/tasks/{taskId}/complete` | 上报任务完成 |
| POST | `/api/v1/agent/tasks/{taskId}/fail` | 上报任务失败 |
| POST | `/api/v1/agent/containers/{serverId}` | 上报容器状态 |
| GET | `/api/v1/agent/version` | 获取 Agent 版本 |

---

### AI

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/ai/insights` | 获取 AI 洞察 |
| GET | `/api/v1/ai/recommendations` | 获取 AI 建议 |
| POST | `/api/v1/ai/recommendations/{id}/apply` | 应用建议 |
| GET | `/api/v1/ai/risk-radar` | 获取风险雷达 |
| GET | `/api/v1/ai/storage-prediction` | 存储预测 |
| POST | `/api/v1/ai/generate-report` | 生成分析报告 |
| GET | `/api/v1/ai/server-analysis/{serverId}` | 服务器分析 |

---

### Recovery

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/recovery/simulate` | 模拟恢复 |
| POST | `/api/v1/recovery/execute` | 执行恢复 |
| POST | `/api/v1/recovery/migrate` | 迁移服务器 |

---

### Risk

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/risk/score` | 获取风险评分 |
| GET | `/api/v1/risk/trend` | 获取风险趋势 |
| GET | `/api/v1/risk/nodes` | 获取风险节点 |
| GET | `/api/v1/risk/list` | 获取风险列表 |
| POST | `/api/v1/risk/{id}/mitigate` | 缓解风险 |
| POST | `/api/v1/risk/scan` | 扫描风险 |

---

### Scheduled Backups

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/scheduled-backups` | 获取定时备份列表 |
| GET | `/api/v1/scheduled-backups/{id}` | 获取定时备份详情 |
| POST | `/api/v1/scheduled-backups` | 创建定时备份 |
| PUT | `/api/v1/scheduled-backups/{id}/toggle` | 启用/禁用定时备份 |
| DELETE | `/api/v1/scheduled-backups/{id}` | 删除定时备份 |

---

### Retention Policies

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/retention-policies` | 获取保留策略列表 |
| GET | `/api/v1/retention-policies/server/{serverId}` | 获取服务器保留策略 |
| POST | `/api/v1/retention-policies` | 创建保留策略 |
| PUT | `/api/v1/retention-policies/{id}/toggle` | 启用/禁用策略 |
| DELETE | `/api/v1/retention-policies/{id}` | 删除保留策略 |
| GET | `/api/v1/retention-policies/{id}/dry-run` | 预览清理结果 |

---

### Server Groups

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/server-groups` | 获取分组列表 |
| POST | `/api/v1/server-groups` | 创建分组 |
| PUT | `/api/v1/server-groups/{id}` | 更新分组 |
| DELETE | `/api/v1/server-groups/{id}` | 删除分组 |
| POST | `/api/v1/server-groups/{groupId}/servers/{serverId}` | 添加服务器到分组 |
| DELETE | `/api/v1/server-groups/servers/{serverId}` | 从分组移除服务器 |

---

### Settings

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/settings/api-keys` | 获取 API Key 列表 |
| POST | `/api/v1/settings/api-keys` | 创建 API Key |
| DELETE | `/api/v1/settings/api-keys/{id}` | 删除 API Key |
| GET | `/api/v1/settings/audit-logs` | 获取审计日志 |
| GET | `/api/v1/settings/audit-logs/search` | 搜索审计日志 |
| GET | `/api/v1/settings/audit-logs/export` | 导出审计日志 |
| GET | `/api/v1/settings/ai-config` | 获取 AI 配置 |
| PUT | `/api/v1/settings/ai-config` | 更新 AI 配置 |

---

### Team

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/team` | 获取团队成员列表 |
| POST | `/api/v1/team/invite` | 邀请成员 |
| PUT | `/api/v1/team/{id}` | 更新成员角色 |
| DELETE | `/api/v1/team/{id}` | 移除成员 |

---

### Terminal

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/terminal/sessions` | 创建终端会话 |
| POST | `/api/v1/terminal/sessions/{sessionId}/exec` | 执行命令 |
| DELETE | `/api/v1/terminal/sessions/{sessionId}` | 关闭会话 |
| GET | `/api/v1/terminal/status` | 获取终端状态 |

---

### Files

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/servers/{serverId}/files/browse` | 浏览文件 |
| GET | `/api/v1/servers/{serverId}/files/read` | 读取文件 |
| GET | `/api/v1/servers/{serverId}/files/download` | 下载文件 |
| POST | `/api/v1/servers/{serverId}/files/upload` | 上传文件 |
| DELETE | `/api/v1/servers/{serverId}/files` | 删除文件 |
| POST | `/api/v1/servers/{serverId}/files/mkdir` | 创建目录 |
| POST | `/api/v1/servers/{serverId}/files/chmod` | 修改权限 |
| GET | `/api/v1/servers/{serverId}/files/stat` | 获取文件信息 |

---

### Webhooks

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/webhooks` | 获取 Webhook 列表 |
| POST | `/api/v1/webhooks` | 创建 Webhook |
| PUT | `/api/v1/webhooks/{id}` | 更新 Webhook |
| DELETE | `/api/v1/webhooks/{id}` | 删除 Webhook |
| GET | `/api/v1/webhooks/{id}/logs` | 获取投递日志 |
| POST | `/api/v1/webhooks/{id}/test` | 测试 Webhook |

---

### Integrations

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/integrations` | 获取集成列表 |
| POST | `/api/v1/integrations` | 创建集成 |
| PUT | `/api/v1/integrations/{id}` | 更新集成 |

---

### Verification Jobs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/verification-jobs` | 获取验证任务列表 |
| POST | `/api/v1/verification-jobs` | 创建验证任务 |
| PUT | `/api/v1/verification-jobs/{id}` | 更新验证任务 |
| DELETE | `/api/v1/verification-jobs/{id}` | 删除验证任务 |
| POST | `/api/v1/verification-jobs/{id}/run` | 执行验证任务 |

---

### Disaster Recovery

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/disaster-recovery` | 获取恢复计划列表 |
| GET | `/api/v1/disaster-recovery/{id}` | 获取恢复计划详情 |
| POST | `/api/v1/disaster-recovery` | 创建恢复计划 |
| PUT | `/api/v1/disaster-recovery/{id}` | 更新恢复计划 |
| DELETE | `/api/v1/disaster-recovery/{id}` | 删除恢复计划 |
| POST | `/api/v1/disaster-recovery/{id}/execute` | 执行恢复计划 |

---

### Change Attribution (Blame)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/servers/{serverId}/blame` | 获取服务器变更归属 |
| GET | `/api/v1/snapshots/{snapshotId}/blame` | 获取快照变更归属 |

---

### Drift Detection

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/servers/{id}/drift` | 检测状态漂移 |

---

## WebSocket Topics

| Topic | Description |
|-------|-------------|
| `/topic/events` | 系统事件推送 |
| `/topic/tasks` | 任务状态更新 |
| `/topic/tasks/{taskId}` | 特定任务进度 |

### 连接方式

```javascript
// SockJS + STOMP
const socket = new SockJS('/ws')
const stompClient = Stomp.over(socket)

stompClient.connect({}, () => {
  stompClient.subscribe('/topic/events', (message) => {
    const event = JSON.parse(message.body)
    console.log('Event:', event)
  })
})
```

---

## 数据模型

### Server

```json
{
  "id": 1,
  "name": "web-server-01",
  "ip": "192.168.1.100",
  "os": "Ubuntu 22.04",
  "status": "ONLINE",
  "uptime": "3 天 5 小时",
  "uptimeSeconds": 273600,
  "sshPort": 22,
  "sshUsername": "root",
  "sshAuthMethod": "KEY",
  "autoSnapshotEnabled": true,
  "groupId": 1
}
```

### Snapshot

```json
{
  "id": 1,
  "name": "每日备份",
  "createdAt": "2026-06-06T10:00:00",
  "status": "SUCCESS",
  "description": "生产环境每日快照",
  "hash": "abc123def456",
  "microserviceCount": 3,
  "serverName": "web-server-01",
  "sizeBytes": 1048576,
  "warning": null,
  "tags": [
    { "id": 1, "name": "production", "color": "#ff5722" }
  ],
  "stateJson": "...",
  "changeSummaryJson": "..."
}
```

### Alert

```json
{
  "id": 1,
  "severity": "WARNING",
  "title": "磁盘空间不足",
  "description": "服务器 /dev/sda1 使用率超过 85%",
  "source": "snapshot-monitor",
  "category": "STORAGE",
  "storagePercent": "85",
  "status": "OPEN",
  "createdAt": "Jun 6, 10:00",
  "hasAutoFix": true
}
```

### AsyncTask

```json
{
  "id": 1,
  "type": "SNAPSHOT",
  "status": "RUNNING",
  "progress": 75,
  "serverId": 1,
  "createdAt": "2026-06-06T10:00:00"
}
```
