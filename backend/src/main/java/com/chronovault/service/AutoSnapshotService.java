package com.chronovault.service;

import com.chronovault.config.DistributedLock;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.snapshot.SnapshotEngine;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSnapshotService {

    private final ServerRepository serverRepository;
    private final SnapshotRepository snapshotRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final SnapshotEngine snapshotEngine;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;
    private final DistributedLock distributedLock;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    private static final int DRIFT_THRESHOLD = 3;
    private static final int COOLDOWN_HOURS = 1;

    /**
     * Check all servers with auto-snapshot enabled for drift.
     * Uses distributed lock to prevent multiple instances from executing simultaneously.
     * Runs every 30 minutes.
     */
    @Scheduled(fixedRate = 1800000)
    @Transactional
    public void checkAndAutoSnapshot() {
        String lockValue = distributedLock.tryLock("auto-snapshot", java.time.Duration.ofMinutes(5));
        if (lockValue == null) return; // Another instance holds the lock

        try {
            List<Server> servers = serverRepository.findByAutoSnapshotEnabledTrueAndStatus(
                    Server.ServerStatus.RUNNING);

            log.info("[AUTO_SNAPSHOT] Checking {} servers for drift", servers.size());
            for (Server server : servers) {
                try {
                    checkServerForDrift(server);
                } catch (Exception e) {
                    log.warn("[AUTO_SNAPSHOT] Check failed for server {}: {}", server.getName(), e.getMessage());
                }
            }
        } finally {
            distributedLock.releaseLock("auto-snapshot", lockValue);
        }
    }

    /**
     * Manually toggle auto-snapshot for a server.
     */
    @Transactional
    public void setAutoSnapshotEnabled(Long serverId, boolean enabled) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new com.chronovault.exception.ResourceNotFoundException("服务器不存在: " + serverId));
        server.setAutoSnapshotEnabled(enabled);
        serverRepository.save(server);
        log.info("Auto-snapshot {} for server {}", enabled ? "enabled" : "disabled", server.getName());
    }

    private void checkServerForDrift(Server server) {
        // Check cooldown: skip if last auto-snapshot was less than 1 hour ago
        if (server.getLastAutoSnapshotAt() != null
                && server.getLastAutoSnapshotAt().plusHours(COOLDOWN_HOURS).isAfter(LocalDateTime.now())) {
            return;
        }

        // Find the latest snapshot for this server
        List<Snapshot> snapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(server.getId());
        Snapshot latestSnapshot = snapshots.stream()
                .filter(s -> s.getHash() != null && s.getType() != Snapshot.SnapshotType.STASH)
                .findFirst()
                .orElse(null);

        if (latestSnapshot == null) {
            // No existing snapshot — skip drift detection (nothing to compare against)
            return;
        }

        // Detect drift: compare current server state vs latest snapshot
        int changes = detectDrift(server, latestSnapshot);
        if (changes >= DRIFT_THRESHOLD) {
            log.info("Server {} has {} changes detected (threshold: {}), creating auto-snapshot",
                    server.getName(), changes, DRIFT_THRESHOLD);
            createAutoSnapshot(server, changes);
        }
    }

    /**
     * Detect drift by comparing current config files against the latest snapshot.
     * Uses MD5 hash comparison of key config files.
     */
    private int detectDrift(Server server, Snapshot latestSnapshot) {
        try {
            SshConnection conn = sshManager.getConnection(server);

            // Get current MD5 hashes of key config files
            String currentHashes = getServerFileHashes(conn);
            if (currentHashes == null || currentHashes.isBlank()) {
                return 0;
            }

            // Get the snapshot's file hashes from manifest or re-compute
            // For simplicity, we use restic ls to get file list and compare
            List<StorageTarget> targets = storageTargetRepository.findAll();
            if (targets.isEmpty()) return 0;

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
            String snapshotFiles = getSnapshotFileHashes(conn, repoUrl, latestSnapshot.getHash());

            if (snapshotFiles == null || snapshotFiles.isBlank()) {
                return 0;
            }

            // Simple hash-based drift detection: count lines that differ
            String[] currentLines = currentHashes.split("\n");
            String[] snapshotLines = snapshotFiles.split("\n");

            java.util.Set<String> currentSet = new java.util.HashSet<>(java.util.List.of(currentLines));
            java.util.Set<String> snapshotSet = new java.util.HashSet<>(java.util.List.of(snapshotLines));

            int changes = 0;
            for (String line : currentLines) {
                if (!snapshotSet.contains(line)) changes++;
            }
            for (String line : snapshotLines) {
                if (!currentSet.contains(line)) changes++;
            }

            return changes;
        } catch (Exception e) {
            log.warn("Drift detection failed for server {}: {}", server.getName(), e.getMessage());
            return 0;
        }
    }

    private String getServerFileHashes(SshConnection conn) {
        String cmd = "find /etc -name '*.conf' -o -name '*.yml' -o -name '*.yaml' -o -name '*.env' 2>/dev/null | "
                + "head -50 | xargs md5sum 2>/dev/null | sort";
        SshConnection.CommandResult result = conn.executeCommand(cmd, java.time.Duration.ofSeconds(30));
        return result.isSuccess() ? result.stdout() : null;
    }

    private String getSnapshotFileHashes(SshConnection conn, String repoUrl, String hash) {
        String restic = resticClient.getResticPath(conn);
        String cmd = String.format("RESTIC_PASSWORD=%s %s ls %s --repo %s 2>/dev/null | head -100",
                resticPassword, restic, hash, repoUrl);
        SshConnection.CommandResult result = conn.executeCommand(cmd, java.time.Duration.ofSeconds(30));
        return result.isSuccess() ? result.stdout() : null;
    }

    private void createAutoSnapshot(Server server, int changeCount) {
        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            log.warn("No storage target available for auto-snapshot on server {}", server.getName());
            return;
        }

        StorageTarget target = targets.stream()
                .filter(t -> t.getType() != StorageTarget.StorageType.LOCAL)
                .findFirst()
                .orElse(targets.get(0));

        try {
            snapshotEngine.createSnapshot(server, target,
                    "自动快照 " + java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    "自动快照: 检测到 " + changeCount + " 个变更",
                    Snapshot.SnapshotType.FULL, null, null, null);

            server.setLastAutoSnapshotAt(LocalDateTime.now());
            serverRepository.save(server);
            log.info("Auto-snapshot created for server {} ({} changes)", server.getName(), changeCount);
        } catch (Exception e) {
            log.error("Failed to create auto-snapshot for server {}: {}", server.getName(), e.getMessage());
        }
    }
}