package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.SnapshotEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSnapshotService {

    private final ServerRepository serverRepository;
    private final SnapshotRepository snapshotRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final SnapshotEngine snapshotEngine;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    private final Map<String, BatchStatus> batches = new ConcurrentHashMap<>();

    /**
     * Start a batch snapshot across multiple servers.
     * Returns a batchId for tracking progress.
     */
    @Transactional
    public String startBatch(List<Long> serverIds, Long storageTargetId, String name, Long userId) {
        if (serverIds == null || serverIds.isEmpty()) {
            throw new BadRequestException("至少需要选择一台服务器");
        }

        StorageTarget storageTarget;
        if (storageTargetId != null) {
            storageTarget = storageTargetRepository.findById(storageTargetId)
                    .orElseThrow(() -> new ResourceNotFoundException("存储目标不存在"));
        } else {
            List<StorageTarget> targets = storageTargetRepository.findAll();
            if (targets.isEmpty()) {
                throw new BadRequestException("没有可用的存储目标");
            }
            storageTarget = targets.stream()
                    .filter(t -> t.getType() != StorageTarget.StorageType.LOCAL)
                    .findFirst().orElse(targets.get(0));
        }

        String batchId = UUID.randomUUID().toString().substring(0, 8);
        List<Server> servers = serverRepository.findAllById(serverIds);

        BatchStatus status = new BatchStatus(batchId, servers.size(), name);
        batches.put(batchId, status);

        // Execute snapshots in parallel (simplified: sequential for now)
        for (Server server : servers) {
            try {
                String title = (name != null ? name : "批量快照") + " - " + server.getName();
                Snapshot snapshot = snapshotEngine.createSnapshot(server, storageTarget, title,
                        "批量快照 #" + batchId, Snapshot.SnapshotType.FULL, userId, null, null);
                status.addResult(server.getId(), server.getName(), snapshot.getId(), "PENDING");
            } catch (Exception e) {
                log.error("Batch snapshot failed for server {}: {}", server.getName(), e.getMessage());
                status.addResult(server.getId(), server.getName(), null, "FAILED: " + e.getMessage());
            }
        }

        status.completedAt = LocalDateTime.now();
        return batchId;
    }

    /**
     * Get the status of a batch snapshot operation.
     */
    public BatchStatus getBatchStatus(String batchId) {
        BatchStatus status = batches.get(batchId);
        if (status == null) {
            throw new ResourceNotFoundException("批量任务不存在: " + batchId);
        }
        return status;
    }

    public static class BatchStatus {
        public String batchId;
        public int totalServers;
        public String name;
        public LocalDateTime createdAt;
        public LocalDateTime completedAt;
        public List<ServerResult> results = new ArrayList<>();

        public BatchStatus() {}
        public BatchStatus(String batchId, int totalServers, String name) {
            this.batchId = batchId;
            this.totalServers = totalServers;
            this.name = name;
            this.createdAt = LocalDateTime.now();
        }

        public void addResult(Long serverId, String serverName, Long snapshotId, String status) {
            results.add(new ServerResult(serverId, serverName, snapshotId, status));
        }

        public static class ServerResult {
            public Long serverId;
            public String serverName;
            public Long snapshotId;
            public String status;

            public ServerResult() {}
            public ServerResult(Long serverId, String serverName, Long snapshotId, String status) {
                this.serverId = serverId;
                this.serverName = serverName;
                this.snapshotId = snapshotId;
                this.status = status;
            }
        }
    }
}