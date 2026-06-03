# ChronoVault — Build & Run Commands

## 环境要求

- Docker & Docker Compose（必须）
- Java 17（Backend 开发）
- Node.js 20 + npm（Frontend 开发）
- Go 1.22（Agent 开发）
- Maven 3.9+（Backend 构建）

---

## 一键启动（开发环境）

```bash
# 启动所有服务（首选）
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f backend
docker-compose logs -f frontend
```

---

## Backend（Spring Boot）

```bash
cd backend

# 编译（不运行测试）
mvn compile -q

# 运行测试
mvn test

# 运行集成测试
mvn verify -P integration-test

# 启动开发服务器（需要先启动 postgres 和 redis）
docker-compose up -d postgres redis
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 检查健康状态
curl -s http://localhost:8080/actuator/health | jq .

# 依赖安全扫描
mvn dependency-check:check

# 查看 Swagger UI
# http://localhost:8080/swagger-ui.html
```

---

## Frontend（Vue 3 + TypeScript）

```bash
cd frontend

# 安装依赖
npm install

# TypeScript 类型检查（零错误才算通过）
npx vue-tsc --noEmit

# 构建
npm run build

# 开发服务器
npm run dev
# 访问: http://localhost:5173

# Lint
npm run lint
```

---

## Agent（Go）

```bash
cd agent

# 编译
go build -o chronovault-agent .

# 运行测试（含竞态检测）
go test -race ./...

# 运行特定测试
go test ./scanner/... -v
go test ./restic/... -v

# 交叉编译（发布用）
GOOS=linux GOARCH=amd64 go build -o dist/chronovault-agent-linux-amd64 .
GOOS=linux GOARCH=arm64 go build -o dist/chronovault-agent-linux-arm64 .
GOOS=darwin GOARCH=amd64 go build -o dist/chronovault-agent-darwin-amd64 .

# 扫描本机环境
./chronovault-agent scan

# 完整状态采集
./chronovault-agent scan --full

# 启动守护进程
./chronovault-agent run --backend-url http://localhost:8080 --token <token>
```

---

## 数据库

```bash
# 连接到 PostgreSQL
docker-compose exec postgres psql -U chronovault -d chronovault

# 查看 Flyway 迁移状态
cd backend && mvn flyway:info

# 手动执行迁移
cd backend && mvn flyway:migrate

# 重置数据库（开发用，危险！）
cd backend && mvn flyway:clean flyway:migrate
```

---

## 全量验证（提交前必须全部通过）

```bash
# 1. Backend 编译 + 单元测试
cd backend && mvn test

# 2. Frontend TypeScript 检查 + 构建
cd frontend && npx vue-tsc --noEmit && npm run build

# 3. Agent 编译 + 测试
cd agent && go build ./... && go test -race ./...

# 4. Docker Compose 启动验证
docker-compose up -d
sleep 15
curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"' && echo "✅ Backend healthy" || echo "❌ Backend unhealthy"
curl -s http://localhost/ | grep -q "ChronoVault" && echo "✅ Frontend loaded" || echo "❌ Frontend failed"
```

---

## 常见问题

### Backend 启动失败
```bash
# 检查数据库连接
docker-compose exec postgres pg_isready -U chronovault

# 检查 application-dev.yml 配置
cat backend/src/main/resources/application-dev.yml
```

### Agent 无法连接 Backend
```bash
# 检查 Backend 是否监听
curl http://localhost:8080/actuator/health

# 检查 Agent 认证 token
./chronovault-agent health-check --backend-url http://localhost:8080
```

### Frontend 构建失败
```bash
# 清除缓存重试
rm -rf node_modules .vite dist
npm install
npm run build
```
