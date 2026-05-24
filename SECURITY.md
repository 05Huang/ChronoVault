# 安全政策

## 支持的版本

| 版本 | 支持状态 |
|------|---------|
| 0.1.x | :white_check_mark: |

## 报告漏洞

如果你发现 ChronoVault 的安全漏洞，请**不要**通过公开 Issue 报告。

请通过以下方式联系我们：

1. **GitHub Security Advisories** — 在仓库页面点击 "Security" > "Report a vulnerability"
2. **邮件** — 发送至 security@chronovault.io

### 报告内容

请包含以下信息：

- 漏洞类型（如 SQL 注入、XSS、凭据泄露等）
- 受影响的组件和版本
- 复现步骤或 PoC
- 潜在影响评估

### 响应流程

- **48 小时内** — 确认收到报告
- **7 个工作日内** — 初步评估并回复
- **修复发布后** — 通知报告者并致谢（如同意）

## 安全最佳实践

### 部署安全

- 使用强随机密钥（`JWT_SECRET`、`CHRONOVAULT_MASTER_KEY`、`CHRONOVAULT_RESTIC_PASSWORD`）
- 生产环境使用 `SPRING_PROFILES_ACTIVE=prod`
- 配置 `CORS_ALLOWED_ORIGins` 限制前端来源
- 启用 SSH known-hosts 文件验证
- 不要使用默认密码，立即修改 demo 账户

### 运行时安全

- 所有 API 端点需要 JWT 认证（除 `/api/auth/login`、`/api/auth/register`）
- 认证端点有 IP 限流保护（30次/分钟）
- Agent API 支持 Bearer token 认证
- SSH 凭据使用 AES-256-GCM 加密存储
- 危险终端命令被自动拦截
