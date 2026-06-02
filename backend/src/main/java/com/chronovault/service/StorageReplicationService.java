package com.chronovault.service;

import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageReplicationService {

    private final SnapshotRepository snapshotRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;
    private final AsyncTaskManager taskManager;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    /**
     * Replicate a snapshot from one storage target to another.
     * Runs asynchronously with progress updates.
     */
    public void replicateSnapshot(Long snapshotId, Long targetStorageId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (snapshot.getHash() == null || snapshot.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据");
        }

        StorageTarget targetStorage = storageTargetRepository.findById(targetStorageId)
                .orElseThrow(() -> new ResourceNotFoundException("目标存储不存在: " + targetStorageId));

        // Find source storage (first available)
        List<StorageTarget> allTargets = storageTargetRepository.findAll();
        StorageTarget sourceStorage = allTargets.stream()
                .filter(t -> !t.getId().equals(targetStorageId))
                .findFirst()
                .orElse(null);

        if (sourceStorage == null) {
            throw new BadRequestException("没有其他存储目标可以复制到");
        }

        taskManager.submit(TaskType.EXPORT, snapshot.getServer().getId(), null,
                "复制快照 " + snapshot.getTitle() + " 到 " + targetStorage.getName(),
                task -> executeReplication(task.getId(), snapshot, sourceStorage, targetStorage));
    }

    private void executeReplication(Long taskId, Snapshot snapshot, StorageTarget source, StorageTarget target) {
        try {
            taskManager.updateProgress(taskId, 10, "连接服务器...");
            SshConnection conn = sshManager.getConnection(snapshot.getServer());

            if (!resticClient.ensureResticInstalled(conn)) {
                throw new RuntimeException("无法安装 restic");
            }

            String sourceRepoUrl = resticClient.buildRepoUrl(source);
            String targetRepoUrl = resticClient.buildRepoUrl(target);

            // Initialize target repo if needed
            taskManager.updateProgress(taskId, 30, "初始化目标仓库...");
            resticClient.init(conn, targetRepoUrl, resticPassword);

            // Copy snapshot
            taskManager.updateProgress(taskId, 50, "复制快照数据...");
            boolean ok = resticClient.copySnapshot(conn, sourceRepoUrl, resticPassword,
                    targetRepoUrl, resticPassword, snapshot.getHash());

            if (!ok) {
                throw new RuntimeException("快照复制失败");
            }

            taskManager.updateProgress(taskId, 100, "快照复制完成");
            log.info("Replicated snapshot {} from {} to {}", snapshot.getId(), source.getName(), target.getName());

        } catch (Exception e) {
            log.error("Replication failed: {}", e.getMessage(), e);
            throw new RuntimeException("复制失败: " + e.getMessage(), e);
        }
    }
}