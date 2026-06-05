# Contributing to ChronoVault

感谢你对 ChronoVault 的贡献！本文档详细说明了开发环境搭建、编码规范、提交流程、分支策略和 Code Review 流程。

---

## Table of Contents

- [开发环境搭建](#开发环境搭建)
- [编码规范](#编码规范)
- [提交规范](#提交规范)
- [分支策略](#分支策略)
- [Code Review 流程](#code-review-流程)
- [测试要求](#测试要求)
- [安全注意事项](#安全注意事项)

---

## 开发环境搭建

### 前置要求

| 工具 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 后端编译运行 |
| Maven | 3.9+ (或使用 `mvnw.cmd`) | 后端构建 |
| Node.js | 20+ | 前端构建 |
| Go | 1.22+ | Agent 编译 |
| Docker | 24+ | 基础设施（PostgreSQL + Redis） |
| Git | 2.30+ | 版本控制 |

### 快速搭建

```bash
# 1. 克隆仓库
git clone https://github.com/chronovault/chronovault.git
cd chronovault

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 设置密钥（参考 README.md）

# 3. 启动基础设施
docker-compose up -d postgres redis

# 4. 启动后端（新终端）
mvnw.cmd spring-boot:run -f backend/pom.xml -Dspring-boot.run.profiles=dev

# 5. 启动前端（新终端）
cd frontend && npm install && npm run dev

# 6. 编译 Agent（可选）
cd agent && go build -o chronovault-agent .
```

### IDE 配置

**IntelliJ IDEA**:
- 安装 Lombok 插件
- 启用 Annotation Processing
- 导入 `.editorconfig`（如果存在）

**VS Code**:
- 安装 Vue - Official 插件
- 安装 Tailwind CSS IntelliSense
- 启用 TypeScript 严格模式

---

## 编码规范

### Java（后端）

#### 基本原则
- 遵循 Google Java Style Guide
- 使用 Lombok 减少样板代码
- 优先使用不可变对象（records, final fields）
- 遇到选择时选简单的，能用标准库解决的不引入新依赖

#### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Package | 小写，复数 | `com.chronovault.controller` |
| Class | PascalCase | `SnapshotService`, `ServerController` |
| Method | camelCase | `createSnapshot()`, `getSnapshotsByTag()` |
| Field | camelCase | `serverId`, `createdAt` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| DTO | PascalCase + DTO/Request/Response | `SnapshotDTO`, `CreateSnapshotRequest` |

#### 注解使用

```java
// Controller: 使用 @Slf4j, @RequiredArgsConstructor
@RestController
@Slf4j
@RequiredArgsConstructor
@Tag(name = "快照管理")
public class SnapshotController {
    private final SnapshotService snapshotService;
    
    @PostMapping
    @Operation(summary = "创建快照", description = "触发服务器快照创建")
    @Auditable(action = "SNAPSHOT_CREATE", resourceType = ResourceType.SNAPSHOT)
    public ResponseEntity<ApiResponse<SnapshotDTO>> createSnapshot(
            @Valid @RequestBody CreateSnapshotRequest request,
            Authentication auth) {
        // ...
    }
}

// Service: 使用 @Slf4j, @Service, @Transactional
@Service
@Slf4j
@RequiredArgsConstructor
public class SnapshotService {
    
    @Transactional
    public Snapshot createSnapshot(CreateSnapshotRequest request, String username) {
        // 业务逻辑
    }
    
    @Transactional(readOnly = true)
    public Page<SnapshotDTO> getSnapshotsPaged(Pageable pageable) {
        // 只读查询
    }
}

// Entity: 使用 @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor
@Entity
@Table(name = "snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Snapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    private Server server;
    
    // ...
}

// DTO: 使用 Java records
public record SnapshotDTO(
    Long id,
    String name,
    String createdAt,
    String status,
    // ...
) {
    public static SnapshotDTO from(Snapshot s) {
        // 工厂方法
    }
}
```

#### 日志规范

```java
// 格式: [操作类型] [目标对象] 结果描述
log.info("[SNAPSHOT_CREATE] [server={}] 快照创建成功, snapshotId={}", serverId, snapshotId);
log.warn("[SSH_CONNECT] [server={}] 连接超时, 重试 {}/{}", serverId, retry, maxRetry);
log.error("[SNAPSHOT_BACKUP] [server={}] 备份失败", serverId, e);
```

#### 安全规范

- SSH 密钥/密码必须通过 `CredentialEncryptor` 加密存储
- 密码/密钥不能出现在日志中（使用 `SensitiveDataMasker`）
- 所有 `@RequestBody` 必须有 Jakarta Validation 注解
- XSS 敏感字段使用 `SanitizeUtil` 过滤

### TypeScript（前端）

#### 基本原则
- 使用 TypeScript 严格模式，避免 `any` 类型
- 组件使用 Composition API（`<script setup lang="ts">`）
- 状态管理使用 Pinia stores
- 样式使用 Tailwind CSS 4 工具类

#### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Component | PascalCase.vue | `SnapshotList.vue` |
| Composable | camelCase + use | `useWebSocket.ts` |
| Store | PascalCase + Store | `auth.ts` → `useAuthStore()` |
| API module | camelCase | `snapshots.ts` |
| Type/Interface | PascalCase | `Snapshot`, `CreateSnapshotRequest` |

#### 组件规范

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import type { Snapshot } from '@/types'

// Props 使用 TypeScript 类型声明
const props = defineProps<{
  serverId: number
  compact?: boolean
}>()

// Emits 类型声明
const emit = defineEmits<{
  (e: 'select', snapshot: Snapshot): void
}>()

// 组合式函数
const { data, loading, error } = useSnapshots(props.serverId)
</script>

<template>
  <div class="glass-panel p-4">
    <!-- 使用 Tailwind CSS 4 + Material Design 3 tokens -->
  </div>
</template>
```

#### API 模块规范

```typescript
// src/api/snapshots.ts
import { client } from './client'
import type { Snapshot, CreateSnapshotRequest, PageResponse } from '@/types'

export const snapshotsApi = {
  list: (params?: { page?: number; size?: number }) =>
    client.get<PageResponse<Snapshot>>('/api/snapshots', { params }),
    
  create: (data: CreateSnapshotRequest) =>
    client.post<Snapshot>('/api/snapshots', data),
    
  diff: (id1: number, id2: number) =>
    client.get(`/api/snapshots/${id1}/diff/${id2}`),
}
```

### Go（Agent）

#### 基本原则
- 遵循 `gofmt` 和 `go vet` 标准
- 错误处理不要忽略，至少记录日志
- 使用 `context.WithTimeout` 控制超时
- 结构化日志使用 `log/slog`

#### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Package | 小写，单数 | `scanner`, `restic` |
| Struct | PascalCase | `StateCollector`, `ResticClient` |
| Method | PascalCase | `Collect()`, `Backup()` |
| Function | camelCase | `parseStateJson()` |
| Variable | camelCase | `serverId`, `retryCount` |
| Constant | PascalCase 或 camelCase | `DefaultTimeout` |

#### 错误处理

```go
// 正确：包装错误上下文
func (c *ResticClient) Backup(ctx context.Context, path string) (*BackupResult, error) {
    cmd := exec.CommandContext(ctx, "restic", "backup", path)
    output, err := cmd.CombinedOutput()
    if err != nil {
        return nil, fmt.Errorf("restic backup failed for %s: %w", path, err)
    }
    return parseBackupOutput(output), nil
}

// 错误：忽略错误
func Bad() {
    result, _ := someOperation() // 不要这样做
}
```

---

## 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### 类型（Type）

| 类型 | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(backend): 新增定时备份功能` |
| `fix` | Bug 修复 | `fix(agent): 修复 restic 路径检测逻辑` |
| `docs` | 文档变更 | `docs: 更新部署指南` |
| `refactor` | 重构（不改变功能） | `refactor(snapshot): 提取验证逻辑到独立方法` |
| `test` | 测试相关 | `test: 为 SnapshotService 添加单元测试` |
| `chore` | 构建/工具变更 | `chore: 升级 Spring Boot 到 3.2.5` |
| `perf` | 性能优化 | `perf(query): 优化快照列表查询避免 N+1` |
| `style` | 代码格式（不影响功能） | `style: 格式化 Controller 代码` |

### 范围（Scope）

| 范围 | 说明 |
|------|------|
| `backend` | 后端通用 |
| `frontend` | 前端通用 |
| `agent` | Agent 通用 |
| `snapshot` | 快照相关 |
| `server` | 服务器管理 |
| `auth` | 认证授权 |
| `alert` | 告警相关 |
| `storage` | 存储相关 |
| `ssh` | SSH 连接 |
| `diff` | 差异对比 |
| `docker` | Docker 操作 |
| `db` | 数据库迁移 |
| `ci` | CI/CD |
| `docs` | 文档 |

### 示例

```bash
# 新功能
git commit -m "feat(backend): 新增服务器克隆功能"

# Bug 修复
git commit -m "fix(agent): 修复 Docker 容器状态采集超时问题"

# 文档更新
git commit -m "docs: 更新 CLAUDE.md 数据库表结构说明"

# 重构
git commit -m "refactor(snapshot): 将快照验证逻辑提取到 SnapshotVerifier 类"

# 测试
git commit -m "test: 为 SnapshotController 添加 MockMvc 测试"
```

### 提交粒度

- **一个提交 = 一个逻辑变更**
- 不要在同一个提交中混合功能开发和代码格式化
- 不要在提交中包含生成的文件（`target/`, `node_modules/`, `dist/`）
- 如果修复了之前的提交，使用 `fixup!` 前缀

---

## 分支策略

### 分支类型

```
main (production)
  │
  ├── develop (integration)
  │     │
  │     ├── feat/snapshot-timeline
  │     ├── feat/selective-rollback
  │     ├── fix/ssh-connection-pool
  │     └── ...
  │
  ├── release/v0.1.0 (release preparation)
  │
  └── hotfix/critical-security-fix
```

| 分支 | 来源 | 合并到 | 说明 |
|------|------|--------|------|
| `main` | - | - | 生产代码，只接受 release 和 hotfix |
| `develop` | `main` | `main` | 开发集成分支 |
| `feat/*` | `develop` | `develop` | 功能开发分支 |
| `fix/*` | `develop` | `develop` | Bug 修复分支 |
| `release/*` | `develop` | `main` + `develop` | 发布准备分支 |
| `hotfix/*` | `main` | `main` + `develop` | 紧急修复分支 |

### 分支命名

```
feat/snapshot-timeline-view
fix/ssh-connection-timeout
docs/update-deployment-guide
refactor/extract-diff-engine
test/integration-snapshot-flow
```

### 工作流程

```bash
# 1. 从 develop 创建功能分支
git checkout develop
git pull origin develop
git checkout -b feat/my-feature

# 2. 开发并提交
git add .
git commit -m "feat(scope): description"

# 3. 保持与 develop 同步
git fetch origin
git rebase origin/develop

# 4. 推送到远程
git push origin feat/my-feature

# 5. 创建 PR 到 develop
# 6. Code Review 通过后合并
# 7. 删除功能分支
```

---

## Code Review 流程

### PR 要求

1. **标题清晰**: 使用 Conventional Commits 格式
2. **描述完整**: 说明变更内容、原因、测试情况
3. **变更最小化**: 每个 PR 只做一件事
4. **测试通过**: CI 检查全部通过
5. **文档更新**: 如有 API 变更，更新相关文档

### Review 检查清单

#### 代码质量
- [ ] 代码符合项目编码规范
- [ ] 没有引入 `any` 类型（TypeScript）
- [ ] 错误处理完善（没有空 catch 块）
- [ ] 没有硬编码的密钥/密码

#### 功能正确性
- [ ] 功能符合需求描述
- [ ] 边界情况处理
- [ ] 空值/异常输入处理

#### 测试覆盖
- [ ] 新增功能有对应测试
- [ ] Bug 修复有回归测试
- [ ] 现有测试仍然通过

#### 安全性
- [ ] 没有 SQL 注入风险
- [ ] 没有 XSS 漏洞
- [ ] 敏感数据加密存储
- [ ] 输入验证完善

#### 性能
- [ ] 没有 N+1 查询
- [ ] 大数据量查询有分页
- [ ] 没有不必要的全表扫描

### Review 规范

#### 作为 Reviewer

- **及时性**: 收到 PR 后 24 小时内完成首次 review
- **建设性**: 提出具体改进建议，不只是说"不好"
- **优先级**: 区分 blocking issues 和 suggestions
- **示例**: 对于代码建议，提供修改示例

```markdown
# Review 示例

## Blocking Issues
- [ ] 安全问题: 第 45 行的密码没有加密存储，必须使用 `CredentialEncryptor`

## Suggestions
- [ ] 性能: 第 82 行的 `findAll()` 改为分页查询，避免大数据量 OOM
- [ ] 可读性: 变量名 `tmp` 改为 `tempSnapshot` 更清晰

## Nits (非阻塞)
- [ ] 格式: 第 12 行缺少空行
```

#### 作为 Author

- **响应及时**: 收到 review 后 24 小时内回复
- **逐条回复**: 对每个 comment 都要回复（同意/解释/已修改）
- **不要 force push**: review 期间避免 force push，保持 commit 历史
- **主动沟通**: 对有争议的设计决策，主动发起讨论

### 合并条件

- [ ] 至少 1 个 Approve（核心模块需要 2 个）
- [ ] CI 检查全部通过
- [ ] 所有 blocking issues 已解决
- [ ] 没有合并冲突

---

## 测试要求

### 后端测试

```bash
# 运行所有测试
mvnw.cmd test -f backend/pom.xml

# 运行单个测试类
mvnw.cmd test -f backend/pom.xml -Dtest=SnapshotServiceTest

# 运行指定包的测试
mvnw.cmd test -f backend/pom.xml -Dtest="com.chronovault.controller.**"
```

#### 测试覆盖要求

| 模块 | 覆盖率要求 | 测试类型 |
|------|-----------|---------|
| Controller | 90%+ | MockMvc 集成测试 |
| Service | 85%+ | 单元测试 (Mockito) |
| Security | 95%+ | 单元测试 |
| Repository | 80%+ | Testcontainers 集成测试 |

### 前端测试

```bash
cd frontend

# 单元测试
npm run test

# E2E 测试
npm run test:e2e

# 类型检查
npx vue-tsc --noEmit
```

### Agent 测试

```bash
cd agent

# 运行所有测试
go test ./...

# 运行带 race detector
go test -race ./...

# 运行指定包
go test ./scanner/...
```

---

## 安全注意事项

### 禁止事项

1. **禁止** 在代码中硬编码密码、密钥、Token
2. **禁止** 在日志中打印敏感信息（密码、SSH 密钥、Token）
3. **禁止** 在公开 Issue/PR 中讨论安全漏洞
4. **禁止** 提交包含真实密钥的 `.env` 文件

### 安全报告

发现安全漏洞请参考 [SECURITY.md](SECURITY.md) 的安全报告流程，**不要**在公开 Issue 中报告。

### 敏感数据处理

```java
// 正确：使用加密存储
String encrypted = credentialEncryptor.encrypt(sshKey);
server.setSshKeyEncrypted(encrypted);

// 正确：日志脱敏
log.info("[SSH_CONNECT] [server={}] 使用密钥 {}", serverId, sensitiveDataMasker.mask(sshKey));

// 错误：明文存储
server.setSshKey(sshKey); // 不要这样做

// 错误：日志打印密钥
log.info("SSH Key: {}", sshKey); // 不要这样做
```

---

## 获取帮助

- **文档**: 查看 `docs/` 目录
- **问题**: 在 GitHub Issues 提问
- **讨论**: 在 GitHub Discussions 讨论

感谢你的贡献！🎉
