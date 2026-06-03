# State Collector 详细规范

## 概述

State Collector 是 ChronoVault 区别于 Backrest 等竞品的核心差异化模块。
它在每次快照时采集服务器的完整系统状态，使后续的 Diff 和选择性回滚成为可能。

---

## 采集模块优先级

| 模块 | 优先级 | 说明 |
|------|--------|------|
| 已安装包列表 | P0 | 核心 diff 依据 |
| systemd 服务状态 | P0 | 核心 diff 依据 |
| 开放端口 | P0 | 安全告警依据 |
| Docker 容器/镜像 | P0 | 大多数用户场景 |
| /etc 配置文件 hash | P0 | 配置 diff 依据 |
| crontab | P1 | 定时任务变更 |
| 环境变量 | P1 | 敏感，只采集 key 不采集 value |
| 内核参数 sysctl | P2 | 高级场景 |
| 挂载点 fstab | P2 | 存储配置 |

---

## 包管理器采集

### apt/dpkg（Debian/Ubuntu）
```bash
# 采集命令
dpkg-query -W -f='${Package}\t${Version}\t${Status}\n' | grep 'install ok installed'
# 输出格式：nginx\t1.24.0-1\tinstall ok installed

# 解析逻辑
for line in output:
    parts = line.split('\t')
    package = {name: parts[0], version: parts[1], manager: "apt"}
```

### rpm/dnf/yum（RHEL/CentOS/Fedora）
```bash
rpm -qa --queryformat '%{NAME}\t%{VERSION}-%{RELEASE}\n'
```

### apk（Alpine）
```bash
apk info -v
```

### 自动检测逻辑
```go
func detectPackageManager() PackageManager {
    if _, err := exec.LookPath("dpkg-query"); err == nil {
        return APT
    }
    if _, err := exec.LookPath("rpm"); err == nil {
        return RPM
    }
    if _, err := exec.LookPath("apk"); err == nil {
        return APK
    }
    return UNKNOWN
}
```

---

## systemd 服务采集

```bash
# 采集命令
systemctl list-units --type=service --all --no-pager --plain --no-legend
# 输出：nginx.service   loaded active running   A high performance web server

# 获取 enabled 状态
systemctl is-enabled nginx.service  # enabled / disabled / static

# 获取 PID（active 服务才有）
systemctl show nginx.service --property=MainPID --value
```

**注意**：
- 只采集 `loaded` 状态的服务（过滤掉 `not-found`）
- 服务数量可能很多（100+），使用并发采集但限制 goroutine 数量（max 10）
- 超时：单个服务查询 5 秒，整体采集 60 秒

---

## 端口采集

```bash
# 首选（现代系统）
ss -tlnp --no-header
# 输出：LISTEN 0 128 0.0.0.0:80 0.0.0.0:* users:(("nginx",pid=1234,fd=6))

# 回退（老系统）
netstat -tlnp 2>/dev/null || ss -tlnp
```

**解析逻辑**：
- 提取 Local Address、Port、State
- 从 users 字段提取进程名和 PID
- 过滤本地回环（127.0.0.1），只保留对外开放的端口

---

## Docker 状态采集

**不使用 docker CLI**，直接调用 Docker Unix socket API（避免 PATH 问题）：

```go
// Go 实现：通过 Unix socket 调用 Docker API
client, err := docker.NewClientWithOpts(
    docker.WithHost("unix:///var/run/docker.sock"),
    docker.WithAPIVersionNegotiation(),
)

// 采集容器列表
containers, err := client.ContainerList(ctx, types.ContainerListOptions{All: true})

// 采集 compose 文件（通过容器 label）
// label: com.docker.compose.project.config_files
```

**如果 Docker socket 不可访问**：返回空的 docker 字段，不报错。

---

## /etc 配置文件采集

### 监控路径列表
```go
var configPaths = []string{
    "/etc/nginx",           // *.conf
    "/etc/apache2",         // *.conf
    "/etc/mysql",           // *.cnf
    "/etc/postgresql",      // *.conf
    "/etc/redis",           // *.conf
    "/etc/crontab",
    "/etc/hosts",
    "/etc/hostname",
    "/etc/resolv.conf",
    "/etc/fstab",
    "/etc/sudoers",         // 高风险，变更必须告警
    "/etc/passwd",          // 只记录 hash，不存内容
    "/etc/ssh/sshd_config",
    "/etc/systemd/system",  // *.service, *.timer
}
```

### 采集逻辑
```go
func collectConfigs(paths []string) []ConfigEntry {
    var entries []ConfigEntry
    for _, path := range paths {
        filepath.Walk(path, func(p string, info os.FileInfo, err error) error {
            if err != nil || info.IsDir() { return nil }
            if info.Size() > 10*1024*1024 { return nil }  // 跳过 > 10MB 的文件
            
            hash, err := sha256File(p)
            if err != nil { return nil }
            
            entries = append(entries, ConfigEntry{
                Path:   p,
                SHA256: hash,
                Size:   info.Size(),
                Mtime:  info.ModTime(),
            })
            return nil
        })
    }
    return entries
}
```

**注意**：只存储 hash，**不存储文件内容**。内容通过 Restic restore 获取（用于 diff 时）。

---

## 性能要求

- 总采集时间：< 30 秒（P99）
- 采集使用 goroutine 并发执行各模块
- 整体超时：60 秒（超时后返回已采集的部分数据 + 超时标志）

```go
func CollectFullState(ctx context.Context) (*StateSnapshot, error) {
    ctx, cancel := context.WithTimeout(ctx, 60*time.Second)
    defer cancel()
    
    var wg sync.WaitGroup
    var mu sync.Mutex
    state := &StateSnapshot{CollectedAt: time.Now()}
    
    // 并发采集
    collectors := []func(){
        func() { state.Packages = collectPackages() },
        func() { state.Services = collectServices() },
        func() { state.Ports = collectPorts() },
        func() { state.Docker = collectDockerState() },
        func() { state.Configs = collectConfigs(configPaths) },
        func() { state.Crontab = collectCrontab() },
    }
    
    for _, c := range collectors {
        wg.Add(1)
        go func(fn func()) {
            defer wg.Done()
            fn()
        }(c)
    }
    
    wg.Wait()
    return state, nil
}
```

---

## 错误处理

每个采集模块独立容错：
- 某个模块失败（如 Docker socket 不存在）→ 返回该字段为空数组，`collection_errors` 字段记录错误
- 不因为一个模块失败而终止整个采集

```json
{
  "collected_at": "...",
  "packages": [...],
  "docker": null,
  "collection_errors": [
    {"module": "docker", "error": "dial unix /var/run/docker.sock: permission denied"}
  ]
}
```
