package com.chronovault.service;

import com.chronovault.dto.snapshot.CreateSnapshotRequest;
import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotDiffDTO;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotDiffRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.snapshot.SnapshotEngine;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final SnapshotRepository snapshotRepository;
    private final SnapshotDiffRepository snapshotDiffRepository;
    private final ServerRepository serverRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final SnapshotEngine snapshotEngine;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;

    private static final String RESTIC_PASSWORD = "chronovault-restic-key";

    @Transactional(readOnly = true)
    public List<SnapshotDTO> getSnapshots() {
        return snapshotRepository.findAll().stream()
                .map(SnapshotDTO::from)
                .toList();
    }

    public SnapshotDTO getSnapshot(Long id) {
        Snapshot snapshot = snapshotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + id));
        return SnapshotDTO.from(snapshot);
    }

    @Transactional
    public SnapshotDTO createSnapshot(CreateSnapshotRequest request, Long userId) {
        Server server = serverRepository.findById(request.serverId())
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + request.serverId()));

        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new RuntimeException("没有可用的存储目标，请先配置存储");
        }

        Snapshot.SnapshotType type = request.type() != null
                ? Snapshot.SnapshotType.valueOf(request.type())
                : Snapshot.SnapshotType.FULL;

        Snapshot snapshot = snapshotEngine.createSnapshot(server, targets.get(0),
                "快照 #" + (snapshotRepository.count() + 1),
                request.note(), type, userId);

        return SnapshotDTO.from(snapshot);
    }

    public List<SnapshotDiffDTO> getSnapshotDiff(Long snapshotId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        // Try to get real diff from restic if we have two snapshots
        List<Snapshot> allSnapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(
                snapshot.getServer().getId());

        if (allSnapshots.size() >= 2 && snapshot.getHash() != null) {
            try {
                Snapshot previous = allSnapshots.stream()
                        .filter(s -> !s.getId().equals(snapshotId))
                        .findFirst().orElse(null);

                if (previous != null && previous.getHash() != null) {
                    SshConnection conn = sshManager.getConnection(snapshot.getServer());
                    List<StorageTarget> targets = storageTargetRepository.findAll();
                    if (!targets.isEmpty()) {
                        String repoUrl = resticClient.buildRepoUrl(targets.get(0));
                        String diffOutput = resticClient.diff(conn, repoUrl, RESTIC_PASSWORD,
                                previous.getHash(), snapshot.getHash());
                        // Parse diff output and convert to SnapshotDiffDTO
                        return parseDiffOutput(snapshot, diffOutput);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get real diff, falling back to DB: {}", e.getMessage());
            }
        }

        return snapshotDiffRepository.findBySnapshotId(snapshotId).stream()
                .map(SnapshotDiffDTO::from)
                .toList();
    }

    @Transactional
    public void rollback(Long snapshotId, Long userId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new RuntimeException("没有可用的存储目标");
        }

        // Use SnapshotEngine for real restore
        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());
            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
            boolean success = resticClient.restore(conn, repoUrl, RESTIC_PASSWORD,
                    snapshot.getHash(), "/");

            if (success) {
                snapshot.setStatus(Snapshot.SnapshotStatus.STABLE);
            } else {
                snapshot.setStatus(Snapshot.SnapshotStatus.WARNING);
            }
            snapshotRepository.save(snapshot);
        } catch (Exception e) {
            log.error("Rollback failed: {}", e.getMessage());
            snapshot.setStatus(Snapshot.SnapshotStatus.WARNING);
            snapshotRepository.save(snapshot);
            throw new RuntimeException("回滚失败: " + e.getMessage());
        }
    }

    private List<SnapshotDiffDTO> parseDiffOutput(Snapshot snapshot, String diffOutput) {
        List<SnapshotDiffDTO> diffs = new java.util.ArrayList<>();
        if (diffOutput == null || diffOutput.isBlank()) return diffs;

        for (String line : diffOutput.lines().toList()) {
            if (line.isBlank()) continue;
            // Parse restic diff output format
            String[] parts = line.split("\\s+", 3);
            if (parts.length >= 3) {
                String action = parts[0];
                String path = parts[2];
                diffs.add(new SnapshotDiffDTO(path,
                        "removed".equals(action) ? "deleted" : null,
                        "added".equals(action) ? "created" : "modified"));
            }
        }
        return diffs;
    }
}
