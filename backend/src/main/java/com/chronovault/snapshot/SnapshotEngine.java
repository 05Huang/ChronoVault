package com.chronovault.snapshot;

import com.chronovault.entity.*;
import com.chronovault.repository.SnapshotManifestRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.storage.StorageRouter;
import com.chronovault.task.AsyncTaskManager;
import com.chronovault.task.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final StorageRouter storageRouter;
    private final AsyncTaskManager taskManager;

    private static final String RESTIC_PASSWORD = "chronovault-restic-key";

    public Snapshot createSnapshot(Server server, StorageTarget storageTarget, String title,
                                    String note, Snapshot.SnapshotType type, Long userId) {
        Snapshot snapshot = Snapshot.builder()
                .server(server)
                .title(title)
                .note(note)
                .type(type)
                .status(Snapshot.SnapshotStatus.STABLE)
                .build();
        snapshot = snapshotRepository.save(snapshot);

        Snapshot finalSnapshot = snapshot;
        taskManager.submit(TaskType.SNAPSHOT, server.getId(), userId,
                "创建快照: " + title,
                task -> executeSnapshot(task.getId(), finalSnapshot, server, storageTarget, type));

        return snapshot;
    }

    private void executeSnapshot(Long taskId, Snapshot snapshot, Server server,
                                  StorageTarget storageTarget, Snapshot.SnapshotType type) {
        try {
            taskManager.updateProgress(taskId, 10, "连接服务器...");
            SshConnection conn = sshManager.getConnection(server);

            String repoUrl = resticClient.buildRepoUrl(storageTarget);

            // Initialize repo if needed
            taskManager.updateProgress(taskId, 20, "初始化存储仓库...");
            resticClient.init(conn, repoUrl, RESTIC_PASSWORD);

            // Pre-snapshot hooks
            taskManager.updateProgress(taskId, 30, "执行预快照钩子...");
            runPreSnapshotHooks(conn);

            // Find parent for incremental
            String parentId = null;
            if (type == Snapshot.SnapshotType.INCREMENTAL) {
                List<Snapshot> existing = snapshotRepository.findByServerIdOrderByCreatedAtDesc(server.getId());
                if (!existing.isEmpty()) {
                    parentId = existing.get(0).getHash(); // hash stores restic snapshot ID
                }
            }

            // Execute backup
            taskManager.updateProgress(taskId, 50, "执行快照备份...");
            List<String> paths = List.of("/");
            List<String> excludes = List.of("/proc", "/sys", "/dev", "/tmp", "/var/cache", "node_modules", ".git");

            ResticClient.ResticSnapshot resticSnapshot = resticClient.backup(
                    conn, repoUrl, RESTIC_PASSWORD, paths, excludes, parentId);

            // Post-snapshot hooks
            taskManager.updateProgress(taskId, 80, "执行后置钩子...");
            runPostSnapshotHooks(conn);

            // Update snapshot record
            if (resticSnapshot != null) {
                snapshot.setHash(resticSnapshot.snapshotId());
                snapshot.setSizeBytes(resticSnapshot.totalBytesProcessed());
            }
            snapshot.setStatus(Snapshot.SnapshotStatus.STABLE);
            snapshotRepository.save(snapshot);

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
        // MySQL lock
        SshConnection.CommandResult mysqlCheck = conn.executeCommand("docker ps --format '{{.Names}}' | grep -i mysql");
        if (mysqlCheck.isSuccess() && !mysqlCheck.stdout().isBlank()) {
            String container = mysqlCheck.stdout().trim().split("\n")[0];
            conn.executeCommand("docker exec " + container + " mysql -e 'FLUSH TABLES WITH READ LOCK;' 2>/dev/null || true");
        }

        // Redis save
        SshConnection.CommandResult redisCheck = conn.executeCommand("docker ps --format '{{.Names}}' | grep -i redis");
        if (redisCheck.isSuccess() && !redisCheck.stdout().isBlank()) {
            String container = redisCheck.stdout().trim().split("\n")[0];
            conn.executeCommand("docker exec " + container + " redis-cli BGSAVE 2>/dev/null || true");
        }
    }

    private void runPostSnapshotHooks(SshConnection conn) {
        // Unlock MySQL
        SshConnection.CommandResult mysqlCheck = conn.executeCommand("docker ps --format '{{.Names}}' | grep -i mysql");
        if (mysqlCheck.isSuccess() && !mysqlCheck.stdout().isBlank()) {
            String container = mysqlCheck.stdout().trim().split("\n")[0];
            conn.executeCommand("docker exec " + container + " mysql -e 'UNLOCK TABLES;' 2>/dev/null || true");
        }
    }

    private void recordManifest(SshConnection conn, Snapshot snapshot, String repoUrl) {
        try {
            SshConnection.CommandResult result = conn.executeCommand(
                    String.format("RESTIC_PASSWORD=%s restic ls %s --repo %s 2>/dev/null | head -1000",
                            RESTIC_PASSWORD, snapshot.getHash(), repoUrl));

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
