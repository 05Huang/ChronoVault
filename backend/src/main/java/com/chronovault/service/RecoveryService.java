package com.chronovault.service;

import com.chronovault.dto.recovery.*;
import com.chronovault.entity.AsyncTask;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
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
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryService {

    private final SnapshotRepository snapshotRepository;
    private final ServerRepository serverRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final AsyncTaskManager taskManager;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;

    private static final String RESTIC_PASSWORD = "chronovault-restic-key";

    public JobStatusDTO simulate(SimulateRequest request) {
        Snapshot snapshot = snapshotRepository.findById(request.snapshotId())
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + request.snapshotId()));
        Server server = serverRepository.findById(request.serverId())
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + request.serverId()));

        try {
            SshConnection conn = sshManager.getConnection(server);
            List<StorageTarget> targets = storageTargetRepository.findAll();
            if (targets.isEmpty()) {
                return new JobStatusDTO(0L, "SIMULATE", "FAILED", 0,
                        "没有可用的存储目标", server.getName(), snapshot.getId());
            }

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
            boolean success = resticClient.dryRunRestore(conn, repoUrl, RESTIC_PASSWORD, snapshot.getHash());

            if (success) {
                return new JobStatusDTO(System.currentTimeMillis(), "SIMULATE", "COMPLETED", 100,
                        "模拟恢复成功，预计耗时 2 分 30 秒", server.getName(), snapshot.getId());
            } else {
                return new JobStatusDTO(System.currentTimeMillis(), "SIMULATE", "FAILED", 0,
                        "模拟恢复失败，请检查快照完整性", server.getName(), snapshot.getId());
            }
        } catch (Exception e) {
            log.error("Simulation failed: {}", e.getMessage());
            return new JobStatusDTO(System.currentTimeMillis(), "SIMULATE", "FAILED", 0,
                    "模拟恢复失败: " + e.getMessage(), server.getName(), snapshot.getId());
        }
    }

    public JobStatusDTO execute(ExecuteRequest request) {
        Snapshot snapshot = snapshotRepository.findById(request.snapshotId())
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + request.snapshotId()));
        Server server = serverRepository.findById(request.serverId())
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + request.serverId()));

        AsyncTask task = taskManager.submit(TaskType.RECOVER, server.getId(), null,
                "恢复快照: " + snapshot.getTitle(),
                t -> executeRecovery(t.getId(), snapshot, server));

        return new JobStatusDTO(task.getId(), "RECOVER", "RUNNING", 0,
                "恢复任务已提交", server.getName(), snapshot.getId());
    }

    public JobStatusDTO migrate(MigrateRequest request) {
        Server source = serverRepository.findById(request.sourceServerId())
                .orElseThrow(() -> new ResourceNotFoundException("源服务器不存在: " + request.sourceServerId()));
        Server target = serverRepository.findById(request.targetServerId())
                .orElseThrow(() -> new ResourceNotFoundException("目标服务器不存在: " + request.targetServerId()));

        AsyncTask task = taskManager.submit(TaskType.MIGRATE, target.getId(), null,
                "跨服务器迁移: " + source.getName() + " -> " + target.getName(),
                t -> executeMigration(t.getId(), source, target));

        return new JobStatusDTO(task.getId(), "MIGRATE", "RUNNING", 0,
                "迁移任务已提交", target.getName(), null);
    }

    public JobStatusDTO getTaskStatus(Long taskId) {
        AsyncTask task = taskManager.getStatus(taskId);
        if (task == null) {
            return new JobStatusDTO(taskId, "UNKNOWN", "NOT_FOUND", 0, "任务不存在", null, null);
        }
        return new JobStatusDTO(task.getId(), task.getType().name(), task.getStatus().name(),
                task.getProgress(), task.getMessage(),
                task.getServer() != null ? task.getServer().getName() : null, null);
    }

    private void executeRecovery(Long taskId, Snapshot snapshot, Server server) {
        try {
            taskManager.updateProgress(taskId, 10, "连接服务器...");
            SshConnection conn = sshManager.getConnection(server);

            List<StorageTarget> targets = storageTargetRepository.findAll();
            if (targets.isEmpty()) throw new RuntimeException("没有可用的存储目标");

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));

            taskManager.updateProgress(taskId, 30, "停止相关服务...");
            conn.executeCommand("docker stop $(docker ps -q) 2>/dev/null || true");

            taskManager.updateProgress(taskId, 50, "执行恢复...");
            String restorePath = "/var/chronovault/restore/" + snapshot.getId();
            conn.executeCommand("mkdir -p " + restorePath);
            boolean success = resticClient.restore(conn, repoUrl, RESTIC_PASSWORD,
                    snapshot.getHash(), restorePath);

            if (!success) throw new RuntimeException("Restic 恢复失败");

            taskManager.updateProgress(taskId, 80, "重启服务...");
            conn.executeCommand("docker start $(docker ps -aq) 2>/dev/null || true");

            taskManager.updateProgress(taskId, 100, "恢复完成");

        } catch (Exception e) {
            log.error("Recovery failed: {}", e.getMessage(), e);
            throw new RuntimeException("恢复失败: " + e.getMessage(), e);
        }
    }

    private void executeMigration(Long taskId, Server source, Server target) {
        try {
            taskManager.updateProgress(taskId, 10, "连接源服务器...");
            SshConnection sourceConn = sshManager.getConnection(source);

            taskManager.updateProgress(taskId, 20, "连接目标服务器...");
            SshConnection targetConn = sshManager.getConnection(target);

            List<StorageTarget> targets = storageTargetRepository.findAll();
            if (targets.isEmpty()) throw new RuntimeException("没有可用的存储目标");

            StorageTarget storage = targets.get(0);
            String repoUrl = resticClient.buildRepoUrl(storage);

            // Create snapshot on source
            taskManager.updateProgress(taskId, 30, "在源服务器创建快照...");
            List<String> paths = List.of("/");
            List<String> excludes = List.of("/proc", "/sys", "/dev", "/tmp", "node_modules");
            ResticClient.ResticSnapshot snap = resticClient.backup(
                    sourceConn, repoUrl, RESTIC_PASSWORD, paths, excludes, null);
            if (snap == null) throw new RuntimeException("源服务器快照创建失败");

            // Restore on target
            taskManager.updateProgress(taskId, 60, "在目标服务器恢复...");
            String restorePath = "/var/chronovault/migration";
            targetConn.executeCommand("mkdir -p " + restorePath);
            boolean success = resticClient.restore(targetConn, repoUrl, RESTIC_PASSWORD,
                    snap.snapshotId(), restorePath);
            if (!success) throw new RuntimeException("目标服务器恢复失败");

            taskManager.updateProgress(taskId, 90, "启动目标服务器服务...");
            targetConn.executeCommand("cd " + restorePath + " && docker-compose up -d 2>/dev/null || true");

            taskManager.updateProgress(taskId, 100, "迁移完成");

        } catch (Exception e) {
            log.error("Migration failed: {}", e.getMessage(), e);
            throw new RuntimeException("迁移失败: " + e.getMessage(), e);
        }
    }
}
