package com.chronovault.service;

import com.chronovault.dto.server.CloneServerRequest;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.entity.User;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.snapshot.SnapshotEngine;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.task.AsyncTaskManager;
import com.chronovault.task.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerCloneService {

    private final ServerRepository serverRepository;
    private final SnapshotRepository snapshotRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final SnapshotEngine snapshotEngine;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;
    private final AsyncTaskManager taskManager;
    private final ServerService serverService;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    /**
     * Clone a server: snapshot source → create target server entry → restore on target.
     * Runs asynchronously with progress updates.
     */
    public void cloneServer(CloneServerRequest request, Long userId) {
        Server source = serverRepository.findById(request.sourceServerId())
                .orElseThrow(() -> new ResourceNotFoundException("源服务器不存在: " + request.sourceServerId()));

        // Check if target IP already exists
        List<Server> existing = serverRepository.findAll();
        boolean targetExists = existing.stream().anyMatch(s -> s.getIp().equals(request.targetServerIp()));
        if (targetExists) {
            throw new BadRequestException("目标IP " + request.targetServerIp() + " 已存在于系统中");
        }

        // Get source storage target
        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }
        StorageTarget storageTarget = targets.stream()
                .filter(t -> t.getType() != StorageTarget.StorageType.LOCAL)
                .findFirst()
                .orElse(targets.get(0));

        String cloneTitle = "克隆 " + source.getName() + " → " + request.targetServerIp();

        taskManager.submit(TaskType.CLONE, source.getId(), userId, cloneTitle,
                task -> executeClone(task.getId(), source, request, storageTarget, userId));
    }

    private void executeClone(Long taskId, Server source, CloneServerRequest request,
                              StorageTarget storageTarget, Long userId) {
        try {
            // Step 1: Create snapshot on source (10-30%)
            taskManager.updateProgress(taskId, 10, "正在创建源服务器快照...");
            Snapshot sourceSnapshot = snapshotEngine.createSnapshot(source, storageTarget,
                    "克隆前快照 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    "为克隆到 " + request.targetServerIp() + " 创建",
                    Snapshot.SnapshotType.FULL, userId, null, null);

            // Wait for snapshot to complete (poll for hash)
            taskManager.updateProgress(taskId, 20, "等待源快照完成...");
            waitForSnapshot(taskId, sourceSnapshot.getId(), 30);

            if (sourceSnapshot.getHash() == null) {
                throw new RuntimeException("源快照创建失败");
            }

            // Step 2: Create target server entry (30-35%)
            taskManager.updateProgress(taskId, 35, "注册目标服务器...");
            String targetName = request.targetName() != null ? request.targetName()
                    : source.getName() + " (克隆)";
            Server target = Server.builder()
                    .user(source.getUser())
                    .name(targetName)
                    .ip(request.targetServerIp())
                    .os(source.getOs())
                    .status(Server.ServerStatus.STOPPED)
                    .sshPort(request.targetSshPort() != null ? request.targetSshPort() : 22)
                    .sshUsername(request.targetSshUsername() != null ? request.targetSshUsername() : "root")
                    .sshAuthMethod(source.getSshAuthMethod())
                    .sshKeyEncrypted(source.getSshKeyEncrypted())
                    .uptimeSeconds(0L)
                    .build();
            serverRepository.save(target);

            // Step 3: SSH into target and restore (35-70%)
            taskManager.updateProgress(taskId, 40, "连接目标服务器...");
            SshConnection targetConn;
            try {
                targetConn = sshManager.getConnection(target);
            } catch (Exception e) {
                throw new RuntimeException("无法连接到目标服务器 " + request.targetServerIp() + ": " + e.getMessage());
            }

            taskManager.updateProgress(taskId, 45, "检查目标服务器 restic...");
            if (!resticClient.ensureResticInstalled(targetConn)) {
                throw new RuntimeException("目标服务器无法安装 restic");
            }

            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            taskManager.updateProgress(taskId, 50, "正在恢复快照到目标服务器...");
            boolean restoreOk = resticClient.restore(targetConn, repoUrl, resticPassword,
                    sourceSnapshot.getHash(), "/");

            if (!restoreOk) {
                throw new RuntimeException("恢复快照到目标服务器失败");
            }

            // Step 4: Verify target (70-90%)
            taskManager.updateProgress(taskId, 70, "验证目标服务器状态...");
            SshConnection verifyConn = sshManager.getConnection(target);
            SshConnection.CommandResult statusCheck = verifyConn.executeCommand("uname -srm && echo OK");
            if (!statusCheck.isSuccess()) {
                log.warn("Target server verification failed, but clone may still be functional");
            }

            // Step 5: Probe target SSH (90-95%)
            taskManager.updateProgress(taskId, 90, "探测目标服务器状态...");
            try {
                SshConnection probeConn = sshManager.getConnection(target);
                SshConnection.CommandResult result = probeConn.executeCommand(
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

            taskManager.updateProgress(taskId, 100, "服务器克隆完成！目标: " + target.getName() + " (" + target.getIp() + ")");
            log.info("Server clone completed: {} → {} ({})", source.getName(), target.getName(), target.getIp());

        } catch (Exception e) {
            log.error("Clone failed: {}", e.getMessage(), e);
            throw new RuntimeException("克隆失败: " + e.getMessage(), e);
        }
    }

    private void waitForSnapshot(Long taskId, Long snapshotId, int targetProgress) {
        int attempts = 0;
        while (attempts < 60) {
            Snapshot snap = snapshotRepository.findById(snapshotId).orElse(null);
            if (snap != null && snap.getHash() != null) {
                taskManager.updateProgress(taskId, targetProgress, "源快照已就绪");
                return;
            }
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            attempts++;
            if (attempts % 5 == 0) {
                taskManager.updateProgress(taskId, 20 + (attempts / 3), "等待源快照完成...");
            }
        }
        throw new RuntimeException("等待源快照超时");
    }
}