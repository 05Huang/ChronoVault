# 贡献指南

感谢你对 ChronoVault 的关注！本文档将帮助你搭建开发环境并了解贡献流程。

## 开发环境搭建

### 前置要求

| 工具 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 后端编译运行 |
| Maven | 3.9+ | 后端构建 |
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
# 编辑 .env 设置密钥（参考 README.md 的环境变量表格）

# 3. 启动基础设施
docker-compose up -d postgres redis

# 4. 启动后端
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 5. 启动前端（新终端）
cd frontend
npm install
npm run dev

# 6. 编译 Agent（可选）
cd agent
go build -o chronovault-agent .
```

## 代码规范

### Java（后端）

- 遵循 Google Java Style Guide
- 使用 Lombok 减少样板代码（`@Getter`, `@Builder`, `@RequiredArgsConstructor`）
- 实体类使用 `@Entity` + `@Table` + Lombok 注解组合
- Controller 返回 `ResponseEntity<ApiResponse<T>>`
- Service 方法使用 `@Transactional`，只读方法加 `readOnly = true`
- DTO 使用 Java `record`，提供静态 `from(Entity)` 工厂方法
- 数据库变更必须通过 Flyway 迁移（`V{N}__description.sql`）

### TypeScript（前端）

- 使用 TypeScript 严格模式，避免 `any` 类型
- API 模块放在 `src/api/`，类型定义放在 `src/types/`
- 组件使用 Composition API（`<script setup lang="ts">`）
- 状态管理使用 Pinia stores
- 样式使用 Tailwind CSS 4 工具类

### Go（Agent）

- 遵循 `gofmt` 和 `go vet` 标准
- 使用 `gorilla/mux` 路由
- 错误处理不要忽略，至少记录日志
- 测试文件命名 `*_test.go`，使用 `httptest` 模拟 HTTP 服务

## 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

类型：
- `feat` — 新功能
- `fix` — Bug 修复
- `docs` — 文档变更
- `refactor` — 重构（不改变功能）
- `test` — 测试相关
- `chore` — 构建/工具变更

示例：
```
feat(backend): 新增定时备份功能
fix(agent): 修复 database.go 的 itoa() 溢出 bug
docs: 更新 README 快速开始指南
```

## PR 流程

1. Fork 仓库，创建功能分支：`git checkout -b feat/my-feature`
2. 确保所有测试通过：
   - 后端：`mvn test`
   - 前端：`npx vue-tsc --noEmit && npm run build`
   - Agent：`go test ./...`
3. 提交 PR，填写说明模板
4. 等待 CI 通过和代码审查
5. 合并后删除功能分支

## 报告 Bug

使用 [GitHub Issues](https://github.com/chronovault/chronovault/issues) 报告，包含：

- 复现步骤
- 期望行为 vs 实际行为
- 环境信息（OS、Java/Node/Go 版本）
- 相关日志或截图

## 安全漏洞

请勿在公开 Issue 中报告安全漏洞。请参考 [SECURITY.md](SECURITY.md) 的安全报告流程。
