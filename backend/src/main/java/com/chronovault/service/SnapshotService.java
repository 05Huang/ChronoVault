package com.chronovault.service;

import com.chronovault.dto.snapshot.CreateSnapshotRequest;
import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotDiffDTO;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.entity.User;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotDiffRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotTagRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.repository.UserRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.snapshot.SnapshotEngine;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final SnapshotRepository snapshotRepository;
    private final SnapshotDiffRepository snapshotDiffRepository;
    private final SnapshotTagRepository tagRepository;
    private final ServerRepository serverRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final UserRepository userRepository;
    private final SnapshotEngine snapshotEngine;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    @Transactional(readOnly = true)
    public List<SnapshotDTO> getSnapshots() {
        List<Snapshot> snapshots = snapshotRepository.findAll();
        return snapshots.stream()
                .map(s -> {
                    List<SnapshotTagDTO> tags = tagRepository.findBySnapshotIdOrderByCreatedAtDesc(s.getId())
                            .stream().map(SnapshotTagDTO::from).toList();
                    return SnapshotDTO.from(s, tags);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SnapshotDTO> getSnapshotsPaged(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return snapshotRepository.findAll(pageable).map(s -> {
            List<SnapshotTagDTO> tags = tagRepository.findBySnapshotIdOrderByCreatedAtDesc(s.getId())
                    .stream().map(SnapshotTagDTO::from).toList();
            return SnapshotDTO.from(s, tags);
        });
    }

    @Transactional(readOnly = true)
    public SnapshotDTO getSnapshot(Long id) {
        Snapshot snapshot = snapshotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + id));
        List<SnapshotTagDTO> tags = tagRepository.findBySnapshotIdOrderByCreatedAtDesc(id)
                .stream().map(SnapshotTagDTO::from).toList();
        return SnapshotDTO.from(snapshot, tags);
    }

    @Transactional
    public SnapshotDTO createSnapshot(CreateSnapshotRequest request, Long userId) {
        log.info("createSnapshot called: serverId={}, storageTargetId={}, type={}, note={}, userId={}",
                request.serverId(), request.storageTargetId(), request.type(), request.note(), userId);

        Server server = serverRepository.findById(request.serverId())
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + request.serverId()));
        log.info("Found server: {} ({})", server.getName(), server.getIp());

        // Use specified storage target, or find an active one
        StorageTarget storageTarget;
        if (request.storageTargetId() != null) {
            storageTarget = storageTargetRepository.findById(request.storageTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("存储目标不存在: " + request.storageTargetId()));
        } else {
            List<StorageTarget> targets = storageTargetRepository.findAll();
            if (targets.isEmpty()) {
                throw new BadRequestException("没有可用的存储目标，请先在存储管理中添加");
            }
            // Prefer non-LOCAL storage targets (S3, OSS, WebDAV) over LOCAL
            storageTarget = targets.stream()
                    .filter(t -> t.getType() != StorageTarget.StorageType.LOCAL)
                    .findFirst()
                    .orElse(targets.get(0));
            log.info("Auto-selected storage target: id={}, type={}", storageTarget.getId(), storageTarget.getType());
        }
        log.info("Using storage target: id={}, type={}, endpoint={}",
                storageTarget.getId(), storageTarget.getType(), storageTarget.getEndpoint());

        Snapshot.SnapshotType type = request.type() != null
                ? Snapshot.SnapshotType.valueOf(request.type())
                : Snapshot.SnapshotType.FULL;
        log.info("Snapshot type: {}", type);

        try {
            Snapshot snapshot = snapshotEngine.createSnapshot(server, storageTarget,
                    "快照 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    request.note(), type, userId);
            log.info("Snapshot created: id={}", snapshot.getId());
            return SnapshotDTO.from(snapshot);
        } catch (Exception e) {
            log.error("SnapshotEngine.createSnapshot failed: {}", e.getMessage(), e);
            throw new BadRequestException("快照创建失败: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
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
                        String diffOutput = resticClient.diff(conn, repoUrl, resticPassword,
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
            throw new BadRequestException("没有可用的存储目标");
        }

        // Use SnapshotEngine for real restore
        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());

            // Ensure restic is installed
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法在目标服务器上安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
            boolean success = resticClient.restore(conn, repoUrl, resticPassword,
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

    @Transactional
    public void deleteSnapshot(Long id) {
        Snapshot snapshot = snapshotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + id));
        snapshotRepository.delete(snapshot);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        List<Snapshot> snapshots = snapshotRepository.findAllById(ids);
        snapshotRepository.deleteAll(snapshots);
        return snapshots.size();
    }

    /**
     * Clean up local restic repository on the server and remove invalid snapshot records.
     */
    public String cleanupLocalRepo() {
        // 1. Delete all null-hash snapshots from DB (useless records)
        List<Snapshot> nullHashSnapshots = snapshotRepository.findAll().stream()
                .filter(s -> s.getHash() == null || s.getHash().isBlank())
                .toList();
        int deletedCount = nullHashSnapshots.size();
        snapshotRepository.deleteAll(nullHashSnapshots);
        log.info("Deleted {} null-hash snapshot records from DB", deletedCount);

        // 2. Delete restic repository on remote server
        List<StorageTarget> targets = storageTargetRepository.findAll();
        StorageTarget localTarget = targets.stream()
                .filter(t -> t.getType() == StorageTarget.StorageType.LOCAL)
                .findFirst().orElse(null);

        String result = "已清理 " + deletedCount + " 条无效快照记录";

        if (localTarget != null) {
            try {
                // Find a server to SSH into
                List<Server> servers = serverRepository.findAll();
                if (!servers.isEmpty()) {
                    SshConnection conn = sshManager.getConnection(servers.get(0));
                    String repoPath = localTarget.getEndpoint();

                    // Check size before deletion
                    SshConnection.CommandResult sizeCheck = conn.executeCommand(
                            "du -sh " + repoPath + " 2>/dev/null | cut -f1");
                    String sizeBefore = sizeCheck.isSuccess() ? sizeCheck.stdout().trim() : "未知";

                    // Delete the repository
                    SshConnection.CommandResult deleteResult = conn.executeCommand(
                            "sudo rm -rf " + repoPath + " 2>&1");

                    if (deleteResult.isSuccess()) {
                        result += "，已删除本地仓库 " + repoPath + "（释放约 " + sizeBefore + "）";
                        log.info("Deleted local restic repo at {} (was {})", repoPath, sizeBefore);
                    } else {
                        result += "，删除本地仓库失败: " + deleteResult.stderr();
                        log.warn("Failed to delete local repo: {}", deleteResult.stderr());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to cleanup local repo: {}", e.getMessage());
                result += "，清理本地仓库时出错: " + e.getMessage();
            }
        }

        return result;
    }
}
