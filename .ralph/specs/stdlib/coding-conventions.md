# 编码规范（所有组件通用）

## Java / Spring Boot

### 命名
- Controller：`XxxController`，方法名动词开头：`createSnapshot`, `getSnapshotDiff`
- Service：`XxxService`，接口 + Impl 模式
- Entity：无后缀，如 `Snapshot`, `Server`, `Alert`
- DTO：`XxxRequest` / `XxxResponse`，如 `CreateSnapshotRequest`, `SnapshotDiffResponse`
- Repository：`XxxRepository extends JpaRepository`

### 异常处理
- 自定义异常：`ChronoVaultException` 基类，子类：`AgentUnreachableException`, `SnapshotNotFoundException` 等
- 全局处理：`@RestControllerAdvice` 的 `GlobalExceptionHandler`，统一返回 `ErrorResponse`
- 不要 catch Exception 后 print stack trace，要么 rethrow，要么转为业务异常

### 日志
```java
// 正确：结构化日志
log.info("Snapshot created: snapshotId={}, serverId={}, duration={}ms", id, serverId, duration);

// 错误：字符串拼接
log.info("Snapshot " + id + " created");
```
- 生产环境不打印敏感信息（密码、SSH 密钥、JWT token）
- 每个重要操作记录 start/end + 关键参数

### 事务
- Service 层方法上标注 `@Transactional`
- 只读操作：`@Transactional(readOnly = true)`
- 长事务拆分：快照创建不要把整个流程放在一个事务里

---

## Go（Agent）

### 命名
- 包名：小写单词，如 `scanner`, `restic`, `transport`
- 文件名：下划线分隔，如 `state_collector.go`, `packages.go`
- 结构体：PascalCase，如 `StateSnapshot`, `PackageInfo`
- 函数：camelCase，导出函数 PascalCase

### 错误处理
```go
// 正确：包装错误，带上下文
if err != nil {
    return nil, fmt.Errorf("collecting packages: %w", err)
}

// 错误：忽略错误
result, _ := collectPackages()
```
- 使用 `errors.Is()` 和 `errors.As()` 而不是字符串比较

### 并发
- goroutine 必须有 recover（防止 panic 导致整个 agent crash）
- 使用 `context.WithTimeout` 控制超时
- 共享状态必须有 mutex 保护

### 命令执行
```go
// 正确：有超时、有错误处理
ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
defer cancel()
cmd := exec.CommandContext(ctx, "dpkg-query", "-W", "-f=${Package}\t${Version}\n")
output, err := cmd.Output()

// 错误：无超时
output, err := exec.Command("dpkg-query", ...).Output()
```

---

## TypeScript / Vue 3

### 组件规范
- 使用 `<script setup lang="ts">` Composition API
- Props 必须有类型定义
- Emit 必须有类型定义

```typescript
// 正确
interface Props {
  snapshotId: string
  showDiff?: boolean
}
const props = defineProps<Props>()

// 错误
const props = defineProps(['snapshotId', 'showDiff'])
```

### API 调用
- 所有 API 调用放在 `src/api/` 目录下，按资源分文件
- 使用 `src/types/` 中定义的类型，不用 `any`
- 错误处理：在 composable 层 catch，向组件暴露 `error` ref

```typescript
// src/api/snapshots.ts
export async function getSnapshotDiff(snapshotAId: string, snapshotBId: string): Promise<SnapshotDiffResponse> {
  const { data } = await axios.get<SnapshotDiffResponse>(`/api/snapshots/diff`, {
    params: { a: snapshotAId, b: snapshotBId }
  })
  return data
}
```

### 状态管理
- 全局状态使用 Pinia store
- Store 文件：`src/stores/xxx.ts`
- 每个 store 独立负责一个资源域

---

## Git 提交规范

格式：`<type>(<scope>): <subject>`

类型：
- `feat`: 新功能
- `fix`: Bug 修复
- `refactor`: 重构（不改变功能）
- `test`: 添加/修改测试
- `docs`: 文档更新
- `perf`: 性能优化
- `security`: 安全修复

示例：
```
feat(agent): implement state.json collector with package/service/docker modules
fix(backend): fix NPE in StateDiffEngine when stateJson is null
test(agent): add unit tests for packages scanner with apt/rpm/apk support
security(backend): enforce SSH known_hosts verification in production
```
