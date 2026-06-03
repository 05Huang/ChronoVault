# Diff Engine 详细规范

## 概述

Diff Engine 接收两个快照的 state.json，计算并返回结构化的变更报告。
这是前端 Git 风格时间线和 Diff 视图的数据来源。

---

## Backend 实现

### `StateDiffEngine.java`

```java
@Service
public class StateDiffEngine {
    
    public SnapshotDiffResult diff(StateSnapshot snapshotA, StateSnapshot snapshotB) {
        return SnapshotDiffResult.builder()
            .packages(diffPackages(snapshotA.getPackages(), snapshotB.getPackages()))
            .services(diffServices(snapshotA.getServices(), snapshotB.getServices()))
            .ports(diffPorts(snapshotA.getPorts(), snapshotB.getPorts()))
            .configs(diffConfigs(snapshotA.getConfigs(), snapshotB.getConfigs()))
            .docker(diffDocker(snapshotA.getDocker(), snapshotB.getDocker()))
            .summary(computeSummary(...))
            .riskLevel(assessRisk(...))
            .build();
    }
}
```

### 包 diff 逻辑
```java
private PackageDiff diffPackages(List<PackageInfo> a, List<PackageInfo> b) {
    // 以包名为 key，建立 Map
    Map<String, String> mapA = a.stream().collect(Collectors.toMap(p -> p.getName(), p -> p.getVersion()));
    Map<String, String> mapB = b.stream().collect(Collectors.toMap(p -> p.getName(), p -> p.getVersion()));
    
    List<PackageChange> added = new ArrayList<>();
    List<PackageChange> removed = new ArrayList<>();
    List<PackageChange> upgraded = new ArrayList<>();
    List<PackageChange> downgraded = new ArrayList<>();
    
    // B 中有但 A 中没有 → added
    // A 中有但 B 中没有 → removed
    // 都有但版本不同 → upgraded 或 downgraded（比较语义版本）
    
    return PackageDiff.builder()
        .added(added).removed(removed).upgraded(upgraded).downgraded(downgraded)
        .build();
}
```

### 配置文件 diff（关键逻辑）
```java
private ConfigDiff diffConfigs(List<ConfigEntry> a, List<ConfigEntry> b) {
    // 通过 SHA-256 比较，找出 hash 不同的文件
    List<ConfigFileDiff> changed = new ArrayList<>();
    
    for (ConfigEntry entryB : b) {
        Optional<ConfigEntry> entryA = a.stream()
            .filter(e -> e.getPath().equals(entryB.getPath()))
            .findFirst();
        
        if (entryA.isEmpty()) {
            // 新增文件
            changed.add(ConfigFileDiff.added(entryB.getPath()));
        } else if (!entryA.get().getSha256().equals(entryB.getSha256())) {
            // 内容变更 → 需要从 Restic 提取内容，生成 unified diff
            String unifiedDiff = fetchAndDiff(
                snapshotAId, entryA.get().getPath(),
                snapshotBId, entryB.getPath()
            );
            changed.add(ConfigFileDiff.modified(entryB.getPath(), unifiedDiff));
        }
    }
    
    // A 中有但 B 中没有 → deleted
    for (ConfigEntry entryA : a) {
        boolean stillExists = b.stream().anyMatch(e -> e.getPath().equals(entryA.getPath()));
        if (!stillExists) {
            changed.add(ConfigFileDiff.deleted(entryA.getPath()));
        }
    }
    
    return ConfigDiff.builder().changed(changed).build();
}
```

### 从 Restic 提取文件内容（用于 diff）
```java
// 通过 Restic dump 提取指定快照中的文件内容
private String extractFileFromSnapshot(String resticSnapshotId, String filePath) {
    // restic -r {repo} dump {snapshot_id} {file_path}
    ProcessResult result = resticClient.dump(resticSnapshotId, filePath);
    return result.getStdout();
}

// 生成 unified diff
private String generateUnifiedDiff(String contentA, String contentB, String filename) {
    // 使用 java-diff-utils 库
    List<String> linesA = Arrays.asList(contentA.split("\n"));
    List<String> linesB = Arrays.asList(contentB.split("\n"));
    Patch<String> patch = DiffUtils.diff(linesA, linesB);
    List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
        "a/" + filename, "b/" + filename, linesA, patch, 3
    );
    return String.join("\n", unifiedDiff);
}
```

---

## 风险评估逻辑

```java
public RiskLevel assessRisk(SnapshotDiffResult diff) {
    // HIGH: 以下任一条件
    if (hasNewHighRiskPort(diff.getPorts()))       return RiskLevel.HIGH;
    if (hasRootPrivilegeChange(diff.getConfigs())) return RiskLevel.HIGH;  // sudoers 变更
    if (hasSSHConfigChange(diff.getConfigs()))     return RiskLevel.HIGH;
    if (hasCriticalServiceDisabled(diff.getServices())) return RiskLevel.HIGH;
    if (hasPackageDowngrade(diff.getPackages()))   return RiskLevel.MEDIUM;
    
    // MEDIUM
    if (diff.getSummary().getConfigsChanged() > 0) return RiskLevel.MEDIUM;
    
    return RiskLevel.LOW;
}

private boolean hasNewHighRiskPort(PortDiff portDiff) {
    Set<Integer> highRiskPorts = Set.of(22, 23, 3306, 5432, 6379, 27017, 9200);
    return portDiff.getOpened().stream()
        .anyMatch(p -> highRiskPorts.contains(p.getPort()));
}
```

---

## 缓存策略

Diff 计算可能耗时（尤其是需要从 Restic 提取文件内容时），结果缓存到 `snapshot_diffs` 表：

```java
@Cacheable(value = "snapshot-diffs", key = "#snapshotAId + ':' + #snapshotBId")
public SnapshotDiffResult getDiff(UUID snapshotAId, UUID snapshotBId) {
    // 先查缓存表
    Optional<SnapshotDiff> cached = snapshotDiffRepository
        .findBySnapshotAIdAndSnapshotBId(snapshotAId, snapshotBId);
    
    if (cached.isPresent()) {
        return deserialize(cached.get().getDiffJson());
    }
    
    // 计算并存储
    SnapshotDiffResult result = computeDiff(snapshotAId, snapshotBId);
    snapshotDiffRepository.save(new SnapshotDiff(snapshotAId, snapshotBId, serialize(result)));
    return result;
}
```

---

## Maven 依赖（需要添加到 pom.xml）

```xml
<!-- Diff 计算 -->
<dependency>
    <groupId>io.github.java-diff-utils</groupId>
    <artifactId>java-diff-utils</artifactId>
    <version>4.12</version>
</dependency>
```

---

## 变更摘要（快照列表优化）

为避免在快照列表接口中逐一计算 diff，快照创建完成时预计算"与上一快照的摘要"并存入 `change_summary_json`：

```json
{
  "packages_added": 0,
  "packages_removed": 0,
  "packages_upgraded": 2,
  "services_changed": 1,
  "ports_opened": 1,
  "ports_closed": 0,
  "configs_changed": 3,
  "docker_containers_changed": 0,
  "risk_level": "HIGH",
  "risk_reasons": ["Port 8080 newly opened"]
}
```

这样快照列表接口直接返回 `change_summary_json`，无需实时计算。
