# ChronoVault Backend

## 环境要求

- **Java 17** 或更高版本
- **Maven 3.8+** (或使用项目自带的 `mvnw`)
- **PostgreSQL 15+**
- **Redis 7+** (可选，用于缓存)

## 快速开始

### 1. 安装依赖

```bash
# 方式一：使用系统 Maven
mvn clean install

# 方式二：使用 Maven Wrapper (无需安装 Maven)
./mvnw clean install        # Linux/Mac
mvnw.cmd clean install      # Windows
```

### 2. 配置数据库

创建 PostgreSQL 数据库：
```sql
CREATE DATABASE chronovault;
```

修改 `src/main/resources/application-dev.yml` 中的数据库连接信息：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chronovault
    username: postgres
    password: postgres
```

### 3. 启动应用

```bash
# 开发环境
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 或使用 Maven Wrapper
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

应用启动后会自动：
- 执行 Flyway 迁移，创建所有数据库表
- 注入演示数据（用户密码：`password123`）
- 启动在 `http://localhost:8080`

### 4. 测试 API

```bash
# 登录获取 JWT Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"xuan@chronovault.io","password":"password123"}'

# 使用 Token 访问其他 API
curl http://localhost:8080/api/servers \
  -H "Authorization: Bearer <your-token>"
```

## 演示用户

| 邮箱 | 密码 | 角色 |
|------|------|------|
| xuan@chronovault.io | password123 | OWNER |
| liwei@chronovault.io | password123 | ADMIN |
| zhangmin@chronovault.io | password123 | MEMBER |

## API 端点

- 认证: `/api/auth`
- 服务器: `/api/servers`
- 快照: `/api/snapshots`
- 恢复: `/api/recovery`
- 告警: `/api/alerts`
- 集成: `/api/integrations`
- 存储: `/api/storage`
- 团队: `/api/team`
- 设置: `/api/settings`
- 风险: `/api/risk`
- AI: `/api/ai`
- 仪表盘: `/api/dashboard`
- WebSocket: `/ws/events`

## 技术栈

- Spring Boot 3.2
- Spring Data JPA + Hibernate
- Spring Security + JWT
- Spring WebSocket + STOMP
- PostgreSQL 15
- Redis 7
- Flyway
- Lombok
