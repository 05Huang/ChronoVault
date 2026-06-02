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
        try {
            taskManager.updateProgress(taskId, 10, "连接服务器...");
            this.currentServerId = server.getId();
            SshConnection conn = sshManager.getConnection(server);

            // Ensure restic is installed on the target server
            taskManager.updateProgress(taskId, 15, "检查备份工具...");
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new RuntimeException("无法在目标服务器上安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            log.info("Restic repo URL: {}", repoUrl);

            // Ensure repo directory exists for local storage
            if (storageTarget.getType() == StorageTarget.StorageType.LOCAL) {
                String mkdirResult = conn.executeCommand(
                        "sudo mkdir -p " + repoUrl + " && sudo chown $(whoami) " + repoUrl + " 2>&1").stdout();
                log.info("mkdir result: {}", mkdirResult);
            }

            // Initialize repo if needed
            taskManager.updateProgress(taskId, 20, "初始化存储仓库...");
            boolean initOk = resticClient.init(conn, repoUrl, resticPassword);
            if (!initOk) {
                log.warn("Restic init returned false (repo may already exist), continuing...");
            }

            // Pre-snapshot hooks
            taskManager.updateProgress(taskId, 30, "执行预快照钩子...");
            runPreSnapshotHooks(conn);

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
                throw new RuntimeException("Restic backup 命令执行失败，请检查服务器连接和存储配置");
            }

            // Post-snapshot hooks
            taskManager.updateProgress(taskId, 80, "执行后置钩子...");
            runPostSnapshotHooks(conn);

            // Capture Docker container state
            taskManager.updateProgress(taskId, 85, "捕获容器状态...");
            captureContainerState(conn, snapshot);

            // Update snapshot record
            snapshot.setHash(resticSnapshot.snapshotId());
            snapshot.setSizeBytes(resticSnapshot.totalBytesProcessed());
            snapshot.setStatus(Snapshot.SnapshotStatus.STABLE);
            snapshotRepository.save(snapshot);
            log.info("Snapshot {} saved with hash={}, size={}", snapshot.getId(),
                    resticSnapshot.snapshotId(), resticSnapshot.totalBytesProcessed());

            // Record manifest
            taskManager.updateProgress(taskId, 90, "记录文件清单...");
            recordManifest(conn, snapshot, repoUrl);

            taskManager.updateProgress(taskId, 100, "快照创建完成");

        } catch (Exception e) {
            log.error("Snapshot failed for server {}: {}", server.getIp(), e.getMessage(), e);
            snapshot.setStatus(Snapshot.SnapshotStatus.WARNING);
            snapshotRepository.save(snapshot);
            throw new RuntimeException("快照创建失败: " + e.getMessage(), e);
        }
    }

    private void runPreSnapshotHooks(SshConnection conn) {
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
            hookService.executeHooks(conn, getCurrentServerId(), SnapshotHook.HookType.PRE_SNAPSHOT);
        } catch (Exception e) {
            log.warn("Failed to execute user pre-snapshot hooks: {}", e.getMessage());
        }
    }

    private void runPostSnapshotHooks(SshConnection conn) {
        // Built-in hooks: Unlock MySQL
        SshConnection.CommandResult mysqlCheck = conn.executeCommand("docker ps --format '{{.Names}}' | grep -i mysql");
        if (mysqlCheck.isSuccess() && !mysqlCheck.stdout().isBlank()) {
            String container = mysqlCheck.stdout().trim().split("\n")[0];
            conn.executeCommand("docker exec " + container + " mysql -e 'UNLOCK TABLES;' 2>/dev/null || true");
        }

        // User-configured hooks
        try {
            hookService.executeHooks(conn, getCurrentServerId(), SnapshotHook.HookType.POST_SNAPSHOT);
        } catch (Exception e) {
            log.warn("Failed to execute user post-snapshot hooks: {}", e.getMessage());
        }
    }

    private Long currentServerId;

    private Long getCurrentServerId() {
        return currentServerId;
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
}
