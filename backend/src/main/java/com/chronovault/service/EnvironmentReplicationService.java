package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.task.AsyncTaskManager;
import com.chronovault.task.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * One-click Environment Replication: creates a test/staging environment
 * from a production server's existing snapshot.
 *
 * Unlike ServerCloneService (which creates a new snapshot then restores),
 * this service uses an existing snapshot for immediate replication.
 *
 * Workflow:
 * 1. Find source server and specified snapshot (or latest)
 * 2. Register a new target server entry
 * 3. SSH into target and restore the snapshot via Restic
 * 4. Verify target server connectivity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentReplicationService {

    private final ServerRepository serverRepository;
    private final SnapshotRepository snapshotRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;
    private final AsyncTaskManager taskManager;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    /**
     * Request to replicate an environment from a snapshot.
     */
    public record ReplicateEnvironmentRequest(
            Long sourceServerId,
            Long snapshotId,        // null = use latest snapshot
            String targetIp,
            String targetName,      // null = auto-generate
            String environment,     // test / staging / dev
            Integer targetSshPort,
            String targetSshUsername
    ) {}

    /**
     * Result of environment replication.
     */
    public record ReplicationResult(
            Long targetServerId,
            String targetName,
            String targetIp,
            Long sourceSnapshotId,
            String status
    ) {}

    /**
     * One-click replicate: create a test/staging environment from a production snapshot.
     * Runs asynchronously with progress updates.
     */
    public void replicateEnvironment(ReplicateEnvironmentRequest request, Long userId) {
        Server source = serverRepository.findById(request.sourceServerId())
                .orElseThrow(() -> new ResourceNotFoundException("源服务器不存在: " + request.sourceServerId()));

        // Validate target IP
        boolean targetExists = serverRepository.existsByIp(request.targetIp());
        if (targetExists) {
            throw new BadRequestException("目标IP " + request.targetIp() + " 已存在于系统中");
        }

        // Find the snapshot to restore from
        Snapshot snapshot;
        if (request.snapshotId() != null) {
            snapshot = snapshotRepository.findById(request.snapshotId())
                    .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + request.snapshotId()));
            if (snapshot.getServer() == null || !snapshot.getServer().getId().equals(source.getId())) {
                throw new BadRequestException("快照不属于源服务器");
            }
        } else {
            // Use latest snapshot
            List<Snapshot> snapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(source.getId());
            snapshot = snapshots.stream()
                    .filter(s -> s.getHash() != null && s.getType() != Snapshot.SnapshotType.STASH)
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("源服务器没有可用的快照"));
        }

        if (snapshot.getHash() == null || snapshot.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据");
        }

        // Get storage target
        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }
        StorageTarget storageTarget = targets.stream()
                .filter(t -> t.getType() != StorageTarget.StorageType.LOCAL)
                .findFirst()
                .orElse(targets.get(0));

        // Build target name
        String envLabel = request.environment() != null ? request.environment() : "test";
        String targetName = request.targetName() != null ? request.targetName()
                : source.getName() + " (" + envLabel + ")";

        // Submit async task
        String taskTitle = String.format("环境复制: %s → %s [%s]",
                source.getName(), request.targetIp(), envLabel);
        taskManager.submit(TaskType.CLONE, source.getId(), userId, taskTitle,
                task -> executeReplication(task.getId(), source, snapshot, storageTarget,
                        request.targetIp(), targetName, envLabel,
                        request.targetSshPort(), request.targetSshUsername()));
    }

    private void executeReplication(Long taskId, Server source, Snapshot snapshot,
                                     StorageTarget storageTarget, String targetIp,
                                     String targetName, String envLabel,
                                     Integer targetSshPort, String targetSshUsername) {
        try {
            // Step 1: Register target server (5-10%)
            taskManager.updateProgress(taskId, 5, "注册目标服务器...");
            Server target = Server.builder()
                    .user(source.getUser())
                    .name(targetName)
                    .ip(targetIp)
                    .os(source.getOs())
                    .status(Server.ServerStatus.STOPPED)
                    .sshPort(targetSshPort != null ? targetSshPort : 22)
                    .sshUsername(targetSshUsername != null ? targetSshUsername : "root")
                    .sshAuthMethod(source.getSshAuthMethod())
                    .sshKeyEncrypted(source.getSshKeyEncrypted())
                    .uptimeSeconds(0L)
                    .build();
            serverRepository.save(target);

            // Step 2: Connect to target (10-20%)
            taskManager.updateProgress(taskId, 10, "连接目标服务器 " + targetIp + "...");
            SshConnection targetConn;
            try {
                targetConn = sshManager.getConnection(target);
            } catch (Exception e) {
                throw new RuntimeException("无法连接到目标服务器 " + targetIp + ": " + e.getMessage());
            }

            // Step 3: Ensure restic on target (20-30%)
            taskManager.updateProgress(taskId, 20, "检查目标服务器 restic...");
            if (!resticClient.ensureResticInstalled(targetConn)) {
                throw new RuntimeException("目标服务器无法安装 restic");
            }

            // Step 4: Restore snapshot (30-70%)
            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            taskManager.updateProgress(taskId, 30, String.format(
                    "正在恢复快照 [%s] 到 %s ...", snapshot.getTitle(), envLabel));
            boolean restoreOk = resticClient.restore(targetConn, repoUrl, resticPassword,
                    snapshot.getHash(), "/");

            if (!restoreOk) {
                throw new RuntimeException("恢复快照到目标服务器失败");
            }

            // Step 5: Verify target (70-90%)
            taskManager.updateProgress(taskId, 70, "验证目标服务器状态...");
            try {
                SshConnection verifyConn = sshManager.getConnection(target);
                SshConnection.CommandResult result = verifyConn.executeCommand(
                        "uname -srm && cat /proc/uptime 2>/dev/null | awk '{print int($1)}'");
                if (result.isSuccess()) {
                    target.setStatus(Server.ServerStatus.RUNNING);
                    String[] lines = result.stdout().trim().split("\n");
                    if (lines.length >= 2) {
                        try { target.setUptimeSeconds(Long.parseLong(lines[1].trim())); } catch (NumberFormatException ignored) {}
                    }
                    serverRepository.save(target);
                }
            } catch (Exception e) {
                log.warn("Target SSH probe failed: {}", e.getMessage());
            }

            // Step 6: Complete (90-100%)
            taskManager.updateProgress(taskId, 90, "完成环境复制...");
            taskManager.updateProgress(taskId, 100, String.format(
                    "环境复制完成！%s [%s] → %s (%s)", source.getName(), envLabel, targetName, targetIp));

            log.info("[ENV_REPLICATE] Completed: {} [{}] → {} ({}) from snapshot {}",
                    source.getName(), envLabel, targetName, targetIp, snapshot.getId());

        } catch (Exception e) {
            log.error("[ENV_REPLICATE] Failed: {}", e.getMessage(), e);
            throw new RuntimeException("环境复制失败: " + e.getMessage(), e);
        }
    }

    /**
     * Get available snapshots for environment replication from a server.
     */
    public List<Map<String, Object>> getAvailableSnapshots(Long serverId) {
        List<Snapshot> snapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId);
        return snapshots.stream()
                .filter(s -> s.getHash() != null && !s.getHash().isBlank()
                        && s.getType() != Snapshot.SnapshotType.STASH)
                .limit(20)
                .map(s -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", s.getId());
                    map.put("title", s.getTitle());
                    map.put("hash", s.getHash());
                    map.put("status", s.getStatus().name());
                    map.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
                    map.put("sizeBytes", s.getSizeBytes());
                    return map;
                })
                .toList();
    }
}
