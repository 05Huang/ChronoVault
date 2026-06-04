package com.chronovault.snapshot;

import com.chronovault.entity.*;
import com.chronovault.repository.ContainerStateRepository;
import com.chronovault.repository.SnapshotManifestRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.service.SnapshotHookService;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.storage.StorageRouter;
import com.chronovault.task.AsyncTaskManager;
import com.chronovault.task.TaskType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chronovault.service.StateCollectionService;
import com.chronovault.service.SnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotEngine {

    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;
    private final SnapshotRepository snapshotRepository;
    private final SnapshotManifestRepository manifestRepository;
    private final ContainerStateRepository containerStateRepository;
    private final SnapshotHookService hookService;
    private final StorageRouter storageRouter;
    private final AsyncTaskManager taskManager;
    private final StateCollectionService stateCollectionService;

    @Lazy
    @Autowired
    private SnapshotService snapshotServiceRef;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    @PostConstruct
    void validateConfig() {
        if (resticPassword == null || resticPassword.isBlank()) {
            throw new IllegalStateException(
                "CHRONOVAULT_RESTIC_PASSWORD environment variable is required. "
                + "Generate one with: openssl rand -hex 32");
        }
    }

    public Snapshot createSnapshot(Server server, StorageTarget storageTarget, String title,
                                    String note, Snapshot.SnapshotType type, Long userId,
                                    List<String> paths, List<String> excludes) {
        Snapshot snapshot = Snapshot.builder()
                .server(server)
                .title(title)
                .note(note)
                .type(type)
                .status(Snapshot.SnapshotStatus.STABLE)
                .build();
        snapshot = snapshotRepository.save(snapshot);

        Snapshot finalSnapshot = snapshot;
        List<String> finalPaths = paths != null && !paths.isEmpty() ? paths : null;
        List<String> finalExcludes = excludes != null && !excludes.isEmpty() ? excludes : null;
        taskManager.submit(TaskType.SNAPSHOT, server.getId(), userId,
                "创建快照: " + title,
                task -> executeSnapshot(task.getId(), finalSnapshot, server, storageTarget, type, finalPaths, finalExcludes));

        return snapshot;
    }

    private void executeSnapshot(Long taskId, Snapshot snapshot, Server server,
                                  StorageTarget storageTarget, Snapshot.SnapshotType type,
                                  List<String> customPaths, List<String> customExcludes) {
        String currentStep = "初始化";
        try {
            currentStep = "连接服务器";
            taskManager.updateProgress(taskId, 10, "连接服务器...");
            SshConnection conn = sshManager.getConnection(server);

            // Ensure restic is installed on the target server
            currentStep = "检查备份工具";
            taskManager.updateProgress(taskId, 15, "检查备份工具...");
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new SnapshotStepException("备份工具安装", "无法在目标服务器上安装 restic 备份工具，请检查 sudo 权限");
            }

            // Pre-flight disk space check
            currentStep = "检查磁盘空间";
            taskManager.updateProgress(taskId, 17, "检查磁盘空间...");
            checkDiskSpace(conn, server);

            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            log.info("Restic repo URL: {}", repoUrl);

            // Ensure repo directory exists for local storage
            if (storageTarget.getType() == StorageTarget.StorageType.LOCAL) {
                currentStep = "准备存储目录";
                String mkdirResult = conn.executeCommand(
                        "sudo mkdir -p " + repoUrl + " && sudo chown $(whoami) " + repoUrl + " 2>&1").stdout();
                log.info("mkdir result: {}", mkdirResult);
            }

            // Initialize repo if needed
            currentStep = "初始化存储仓库";
            taskManager.updateProgress(taskId, 20, "初始化存储仓库...");
            boolean initOk = resticClient.init(conn, repoUrl, resticPassword);
            if (!initOk) {
                log.warn("Restic init returned false (repo may already exist), continuing...");
            }

            // Pre-snapshot hooks
            currentStep = "执行预快照钩子";
            taskManager.updateProgress(taskId, 30, "执行预快照钩子...");
            runPreSnapshotHooks(conn, server.getId());

            // Find parent for incremental
            String parentId = null;
            if (type == Snapshot.SnapshotType.INCREMENTAL) {
                List<Snapshot> existing = snapshotRepository.findByServerIdOrderByCreatedAtDesc(server.getId());
                for (Snapshot s : existing) {
                    if (s.getHash() != null && !s.getHash().isBlank()) {
                        parentId = s.getHash();
                        break;
                    }
                }
            }

            // Execute backup
            currentStep = "执行备份";
            taskManager.updateProgress(taskId, 50, "执行快照备份...");
            List<String> paths = customPaths != null && !customPaths.isEmpty()
                    ? customPaths
                    : List.of("/");
            List<String> excludes = customExcludes != null && !customExcludes.isEmpty()
                    ? customExcludes
                    : List.of("/proc", "/sys", "/dev", "/tmp", "/var/cache", "node_modules", ".git");

            ResticClient.ResticSnapshot resticSnapshot = resticClient.backup(
                    conn, repoUrl, resticPassword, paths, excludes, parentId);

            if (resticSnapshot == null) {
                throw new SnapshotStepException("备份执行", "Restic backup 命令执行失败，请检查服务器连接和存储配置");
            }

            // Post-snapshot hooks
            currentStep = "执行后置钩子";
            taskManager.updateProgress(taskId, 80, "执行后置钩子...");
            runPostSnapshotHooks(conn, server.getId());

            // Capture Docker container state
            taskManager.updateProgress(taskId, 85, "捕获容器状态...");
            captureContainerState(conn, snapshot);

            // Capture system state.json (the core differentiator)
            taskManager.updateProgress(taskId, 88, "采集系统状态...");
            try {
                String stateJson = stateCollectionService.collectStateViaSsh(conn);
                if (stateJson != null && !stateJson.isBlank()) {
                    // P3-2: Optimize large state.json to prevent DB bloat
                    // If state_json exceeds 1MB, truncate the packages array
                    // to keep storage under control while preserving diff capability
                    if (stateJson.length() > 1_048_576) {
                        log.warn("Large state.json detected ({} bytes) for snapshot {}, truncating packages array",
                                stateJson.length(), snapshot.getId());
                        stateJson = optimizeLargeStateJson(stateJson);
                    }
                    snapshot.setStateJson(stateJson);
                    snapshot.setStateCollectedAt(LocalDateTime.now());
                    log.info("Captured state.json for snapshot {} ({} bytes)",
                            snapshot.getId(), stateJson.length());
                }
            } catch (Exception e) {
                log.warn("State collection failed for snapshot {}: {}", snapshot.getId(), e.getMessage());
                // Continue — state collection is not critical for backup success
            }

            // Update snapshot record
            snapshot.setHash(resticSnapshot.snapshotId());
            snapshot.setSizeBytes(resticSnapshot.totalBytesProcessed());
            snapshot.setStatus(Snapshot.SnapshotStatus.STABLE);
            snapshotRepository.save(snapshot);
            log.info("Snapshot {} saved with hash={}, size={}", snapshot.getId(),
                    resticSnapshot.snapshotId(), resticSnapshot.totalBytesProcessed());

            // Compute change summary and detect high-risk changes (P1-4)
            taskManager.updateProgress(taskId, 92, "分析变更...");
            try {
                // Find the previous snapshot for this server
                List<Snapshot> previousSnapshots = snapshotRepository
                        .findByServerIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                                server.getId(), snapshot.getCreatedAt());
                if (!previousSnapshots.isEmpty() && snapshot.getStateJson() != null) {
                    Snapshot previous = previousSnapshots.get(previousSnapshots.size() - 1);
                    // Compute and cache change summary
                    snapshotServiceRef.computeAndCacheChangeSummary(snapshot);
                    // Detect high-risk changes and create alerts
                    snapshotServiceRef.detectAndAlertHighRiskChanges(previous, snapshot);
                }
            } catch (Exception e) {
                log.warn("Change analysis failed for snapshot {}: {}", snapshot.getId(), e.getMessage());
                // Continue — analysis is not critical
            }

            // Record manifest
            taskManager.updateProgress(taskId, 95, "记录文件清单...");
            recordManifest(conn, snapshot, repoUrl);

            taskManager.updateProgress(taskId, 100, "快照创建完成");

        } catch (SnapshotStepException e) {
            log.error("Snapshot failed at step '{}' for server {}: {}", currentStep, server.getIp(), e.getMessage(), e);
            snapshot.setStatus(Snapshot.SnapshotStatus.WARNING);
            snapshotRepository.save(snapshot);
            throw new RuntimeException("快照创建失败 [" + currentStep + "]: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Snapshot failed at step '{}' for server {}: {}", currentStep, server.getIp(), e.getMessage(), e);
            snapshot.setStatus(Snapshot.SnapshotStatus.WARNING);
            snapshotRepository.save(snapshot);
            throw new RuntimeException("快照创建失败 [" + currentStep + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Pre-flight check: verify that the target server has enough disk space for a backup.
     * Warns if less than 1GB free, refuses if less than 100MB.
     */
    private void checkDiskSpace(SshConnection conn, Server server) {
        try {
            SshConnection.CommandResult result = conn.executeCommand(
                    "df -h / | tail -1 | awk '{print $4}'", java.time.Duration.ofSeconds(10));
            if (result.isSuccess() && !result.stdout().isBlank()) {
                String freeSpace = result.stdout().trim();
                log.info("[PREFLIGHT] Server {} disk space: {} free", server.getIp(), freeSpace);

                // Parse human-readable size to bytes for comparison
                long freeBytes = parseHumanReadableSize(freeSpace);
                if (freeBytes < 100 * 1024 * 1024L) { // < 100MB
                    throw new SnapshotStepException("磁盘空间检查",
                            "服务器磁盘空间不足（剩余 " + freeSpace + "），至少需要 100MB 可用空间");
                }
                if (freeBytes < 1024 * 1024 * 1024L) { // < 1GB
                    log.warn("[PREFLIGHT] Server {} has low disk space: {} free (recommended: >1GB)", server.getIp(), freeSpace);
                }
            }
        } catch (SnapshotStepException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PREFLIGHT] Failed to check disk space on {}: {}", server.getIp(), e.getMessage());
            // Don't block the snapshot if disk check fails — just warn
        }
    }

    /**
     * Parse human-readable disk size (e.g., "12G", "500M", "1024K") to bytes.
     */
    private long parseHumanReadableSize(String size) {
        if (size == null || size.isBlank()) return Long.MAX_VALUE;
        size = size.trim().toUpperCase();
        try {
            if (size.endsWith("G")) return Long.parseLong(size.substring(0, size.length() - 1)) * 1024 * 1024 * 1024L;
            if (size.endsWith("M")) return Long.parseLong(size.substring(0, size.length() - 1)) * 1024 * 1024L;
            if (size.endsWith("K")) return Long.parseLong(size.substring(0, size.length() - 1)) * 1024L;
            if (size.endsWith("T")) return Long.parseLong(size.substring(0, size.length() - 1)) * 1024 * 1024 * 1024L * 1024L;
            return Long.parseLong(size); // Assume bytes
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE; // Can't parse, assume enough space
        }
    }

    private void runPreSnapshotHooks(SshConnection conn, Long serverId) {
        // Built-in hooks: MySQL lock
        SshConnection.CommandResult mysqlCheck = conn.executeCommand("docker ps --format '{{.Names}}' | grep -i mysql");
        if (mysqlCheck.isSuccess() && !mysqlCheck.stdout().isBlank()) {
            String container = mysqlCheck.stdout().trim().split("\n")[0];
            conn.executeCommand("docker exec " + container + " mysql -e 'FLUSH TABLES WITH READ LOCK;' 2>/dev/null || true");
        }

        // Built-in hooks: Redis save
        SshConnection.CommandResult redisCheck = conn.executeCommand("docker ps --format '{{.Names}}' | grep -i redis");
        if (redisCheck.isSuccess() && !redisCheck.stdout().isBlank()) {
            String container = redisCheck.stdout().trim().split("\n")[0];
            conn.executeCommand("docker exec " + container + " redis-cli BGSAVE 2>/dev/null || true");
        }

        // User-configured hooks
        try {
            hookService.executeHooks(conn, serverId, SnapshotHook.HookType.PRE_SNAPSHOT);
        } catch (Exception e) {
            log.warn("Failed to execute user pre-snapshot hooks: {}", e.getMessage());
        }
    }

    private void runPostSnapshotHooks(SshConnection conn, Long serverId) {
        // Built-in hooks: Unlock MySQL
        SshConnection.CommandResult mysqlCheck = conn.executeCommand("docker ps --format '{{.Names}}' | grep -i mysql");
        if (mysqlCheck.isSuccess() && !mysqlCheck.stdout().isBlank()) {
            String container = mysqlCheck.stdout().trim().split("\n")[0];
            conn.executeCommand("docker exec " + container + " mysql -e 'UNLOCK TABLES;' 2>/dev/null || true");
        }

        // User-configured hooks
        try {
            hookService.executeHooks(conn, serverId, SnapshotHook.HookType.POST_SNAPSHOT);
        } catch (Exception e) {
            log.warn("Failed to execute user post-snapshot hooks: {}", e.getMessage());
        }
    }

    private void captureContainerState(SshConnection conn, Snapshot snapshot) {
        try {
            // List running containers
            SshConnection.CommandResult psResult = conn.executeCommand(
                    "docker ps --format '{{.Names}}\\t{{.Image}}\\t{{.Status}}' 2>/dev/null");
            if (!psResult.isSuccess() || psResult.stdout().isBlank()) {
                log.info("No Docker containers found on server");
                return;
            }

            List<ContainerState> states = new ArrayList<>();
            for (String line : psResult.stdout().lines().toList()) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\t", 3);
                if (parts.length < 2) continue;

                String name = parts[0].trim();
                String image = parts.length > 1 ? parts[1].trim() : "";
                String status = parts.length > 2 ? parts[2].trim() : "";

                // Get detailed info via docker inspect
                SshConnection.CommandResult inspectResult = conn.executeCommand(
                        "docker inspect " + name + " --format '{{json .HostConfig.PortBindings}}|{{json .Mounts}}|{{json .NetworkSettings.Networks}}' 2>/dev/null");

                String ports = "[]";
                String volumes = "[]";
                String networks = "[]";

                if (inspectResult.isSuccess() && !inspectResult.stdout().isBlank()) {
                    String[] inspectParts = inspectResult.stdout().trim().split("\\|", 3);
                    if (inspectParts.length > 0) ports = inspectParts[0].trim();
                    if (inspectParts.length > 1) volumes = inspectParts[1].trim();
                    if (inspectParts.length > 2) networks = inspectParts[2].trim();
                }

                ContainerState state = ContainerState.builder()
                        .snapshot(snapshot)
                        .containerName(name)
                        .image(image)
                        .status(status)
                        .ports(ports)
                        .volumes(volumes)
                        .networks(networks)
                        .build();
                states.add(state);
            }

            if (!states.isEmpty()) {
                containerStateRepository.saveAll(states);
                log.info("Captured {} container states for snapshot {}", states.size(), snapshot.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to capture container state: {}", e.getMessage());
        }
    }

    private void recordManifest(SshConnection conn, Snapshot snapshot, String repoUrl) {
        try {
            String restic = resticClient.getResticPath(conn);
            SshConnection.CommandResult result = conn.executeCommand(
                    String.format("RESTIC_PASSWORD=%s %s ls %s --repo %s 2>/dev/null | head -1000",
                            resticPassword, restic, snapshot.getHash(), repoUrl));

            if (result.isSuccess()) {
                List<SnapshotManifest> manifests = new ArrayList<>();
                for (String line : result.stdout().lines().toList()) {
                    if (line.isBlank()) continue;
                    SnapshotManifest manifest = SnapshotManifest.builder()
                            .snapshot(snapshot)
                            .filePath(line)
                            .fileType(classifyFileType(line))
                            .build();
                    manifests.add(manifest);
                }
                manifestRepository.saveAll(manifests);
            }
        } catch (Exception e) {
            log.warn("Failed to record manifest: {}", e.getMessage());
        }
    }

    private String classifyFileType(String path) {
        if (path.contains("/etc/nginx") || path.endsWith(".conf")) return "CONFIG";
        if (path.contains("/var/lib/mysql") || path.endsWith(".sql")) return "DATABASE";
        if (path.endsWith(".env") || path.endsWith(".yml") || path.endsWith(".yaml")) return "ENV";
        if (path.contains("docker-compose")) return "COMPOSE";
        return "FILE";
    }

    /**
     * Optimize large state.json by truncating the packages array.
     * When state_json exceeds 1MB, this keeps the most important data
     * while reducing storage footprint. The diff engine still works
     * correctly on truncated data.
     */
    private String optimizeLargeStateJson(String stateJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(stateJson);

            com.fasterxml.jackson.databind.node.ObjectNode result = mapper.createObjectNode();
            // Copy non-packages fields as-is
            result.set("collected_at", root.get("collected_at"));
            result.set("agent_version", root.get("agent_version"));
            result.set("os", root.get("os"));
            result.set("services", root.get("services"));
            result.set("ports", root.get("ports"));
            result.set("docker", root.get("docker"));
            result.set("configs", root.get("configs"));
            result.set("crontab", root.get("crontab"));

            // Truncate packages to first 5000 entries
            com.fasterxml.jackson.databind.JsonNode packages = root.get("packages");
            if (packages != null && packages.isArray()) {
                com.fasterxml.jackson.databind.node.ArrayNode truncatedPackages = mapper.createArrayNode();
                int count = 0;
                for (com.fasterxml.jackson.databind.JsonNode pkg : packages) {
                    if (count >= 5000) {
                        log.info("Truncated packages array from {} to 5000 entries", packages.size());
                        break;
                    }
                    truncatedPackages.add(pkg);
                    count++;
                }
                result.set("packages", truncatedPackages);
            } else {
                result.set("packages", mapper.createArrayNode());
            }

            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("Failed to optimize large state.json: {}", e.getMessage());
            return stateJson; // Return original if optimization fails
        }
    }
}
