package com.chronovault.service;

import com.chronovault.dto.snapshot.CherryPickRequest;
import com.chronovault.dto.snapshot.CreateSnapshotRequest;
import com.chronovault.dto.snapshot.SelectiveRestoreRequest;
import com.chronovault.dto.snapshot.SnapshotVerifyResult;
import com.chronovault.dto.snapshot.SnapshotFileEntry;
import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotDiffDTO;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.entity.Alert;
import com.chronovault.entity.AuditLog;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.entity.User;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.exception.RollbackFailedException;
import com.chronovault.repository.AlertRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotDiffRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotTagRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.repository.UserRepository;
import com.chronovault.diff.StateDiffEngine;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final SnapshotRepository snapshotRepository;
    private final SnapshotDiffRepository snapshotDiffRepository;
    private final com.chronovault.metrics.BackupMetrics backupMetrics;
    private final SnapshotTagRepository tagRepository;
    private final ServerRepository serverRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final UserRepository userRepository;
    private final SnapshotEngine snapshotEngine;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;
    private final ChangeAttributionService attributionService;
    private final StateDiffEngine stateDiffEngine;
    private final AlertRepository alertRepository;
    private final NotificationService notificationService;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    /**
     * Get the preferred storage target: first non-LOCAL, or any target if none exist.
     * Avoids loading all targets from DB every time.
     */
    private StorageTarget getPreferredStorageTarget() {
        StorageTarget target = storageTargetRepository.findFirstNonLocal();
        if (target == null) {
            target = storageTargetRepository.findFirst();
        }
        return target;
    }

    /**
     * Get any storage target, throwing if none exist.
     */
    private StorageTarget getAnyStorageTarget() {
        StorageTarget target = storageTargetRepository.findFirst();
        if (target == null) {
            throw new BadRequestException("没有可用的存储目标，请先在存储管理中添加");
        }
        return target;
    }

    @Transactional(readOnly = true)
    public List<SnapshotDTO> getSnapshots() {
        List<Snapshot> snapshots = snapshotRepository.findAll();
        // Batch load tags to avoid N+1 queries
        List<Long> snapshotIds = snapshots.stream().map(Snapshot::getId).toList();
        Map<Long, List<SnapshotTagDTO>> tagsBySnapshot = new java.util.HashMap<>();
        if (!snapshotIds.isEmpty()) {
            Map<Long, List<SnapshotTagDTO>> grouped = tagRepository.findBySnapshotIdsIn(snapshotIds).stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            t -> t.getSnapshot().getId(),
                            java.util.stream.Collectors.mapping(SnapshotTagDTO::from, java.util.stream.Collectors.toList())));
            grouped.forEach((k, v) -> tagsBySnapshot.put(k, v));
        }
        return snapshots.stream()
                .map(s -> SnapshotDTO.from(s, tagsBySnapshot.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SnapshotDTO> getSnapshotsByTag(String tagName) {
        // Use JOIN query instead of loading all snapshots and filtering in memory
        List<Snapshot> snapshots = snapshotRepository.findByTagName(tagName);
        return snapshots.stream()
                .map(s -> {
                    List<SnapshotTagDTO> tags = tagRepository.findBySnapshotIdOrderByCreatedAtDesc(s.getId())
                            .stream().map(SnapshotTagDTO::from).toList();
                    return SnapshotDTO.from(s, tags);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SnapshotDTO> getSnapshotsByTagPaged(String tagName, int page, int size, String sort, String direction) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = (sort != null && !sort.isBlank()) ? sort : "createdAt";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(dir, sortField));
        return snapshotRepository.findByTagNamePaged(tagName, pageable)
                .map(s -> {
                    List<SnapshotTagDTO> tags = tagRepository.findBySnapshotIdOrderByCreatedAtDesc(s.getId())
                            .stream().map(SnapshotTagDTO::from).toList();
                    return SnapshotDTO.from(s, tags);
                });
    }

    @Transactional(readOnly = true)
    public Page<SnapshotDTO> getSnapshotsPaged(int page, int size, String sort, String direction) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = (sort != null && !sort.isBlank()) ? sort : "createdAt";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(dir, sortField));
        Page<Snapshot> snapshotPage = snapshotRepository.findAll(pageable);

        // Batch load tags to avoid N+1 queries
        List<Long> snapshotIds = snapshotPage.getContent().stream().map(Snapshot::getId).toList();
        Map<Long, List<SnapshotTagDTO>> tagsBySnapshot = new java.util.HashMap<>();
        if (!snapshotIds.isEmpty()) {
            Map<Long, List<SnapshotTagDTO>> grouped = tagRepository.findBySnapshotIdsIn(snapshotIds).stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            t -> t.getSnapshot().getId(),
                            java.util.stream.Collectors.mapping(SnapshotTagDTO::from, java.util.stream.Collectors.toList())));
            grouped.forEach((k, v) -> tagsBySnapshot.put(k, v));
        }

        return snapshotPage.map(s -> SnapshotDTO.from(s, tagsBySnapshot.getOrDefault(s.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public SnapshotDTO getSnapshot(Long id) {
        Snapshot snapshot = snapshotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + id));
        List<SnapshotTagDTO> tags = tagRepository.findBySnapshotIdOrderByCreatedAtDesc(id)
                .stream().map(SnapshotTagDTO::from).toList();
        return SnapshotDTO.fromDetail(snapshot, tags);
    }

    @Transactional
    public SnapshotDTO createSnapshot(CreateSnapshotRequest request, Long userId) {
        log.info("[SNAPSHOT_CREATE] [server={}] Initiating snapshot (type={}, storage={})",
                request.serverId(), request.type(), request.storageTargetId());

        Server server = serverRepository.findById(request.serverId())
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + request.serverId()));
        log.debug("[SNAPSHOT_CREATE] [server={}] Resolved server: {} ({})", request.serverId(), server.getName(), server.getIp());

        // Use specified storage target, or find an active one
        StorageTarget storageTarget;
        if (request.storageTargetId() != null) {
            storageTarget = storageTargetRepository.findById(request.storageTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("存储目标不存在: " + request.storageTargetId()));
        } else {
            storageTarget = getPreferredStorageTarget();
            if (storageTarget == null) {
                throw new BadRequestException("没有可用的存储目标，请先在存储管理中添加");
            }
            log.debug("[SNAPSHOT_CREATE] [server={}] Auto-selected storage target: id={}, type={}", request.serverId(), storageTarget.getId(), storageTarget.getType());
        }
        log.debug("[SNAPSHOT_CREATE] [server={}] Using storage target: id={}, type={}, endpoint={}",
                request.serverId(), storageTarget.getId(), storageTarget.getType(), storageTarget.getEndpoint());

        Snapshot.SnapshotType type = request.type() != null
                ? Snapshot.SnapshotType.valueOf(request.type())
                : Snapshot.SnapshotType.FULL;
        log.debug("[SNAPSHOT_CREATE] [server={}] Snapshot type: {}", request.serverId(), type);

        try {
            // Use user-provided title if available, otherwise generate default
            String title = (request.title() != null && !request.title().isBlank())
                    ? request.title()
                    : "快照 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
            Snapshot snapshot = snapshotEngine.createSnapshot(server, storageTarget,
                    title,
                    request.note(), type, userId,
                    request.paths(), request.excludes());
            log.info("[SNAPSHOT_CREATE] [server={}] [snapshot={}] Snapshot created successfully", request.serverId(), snapshot.getId());
            backupMetrics.recordSnapshot();

            // Record blame
            User user = userRepository.findById(userId).orElse(null);
            attributionService.record(AuditLog.ChangeType.SNAPSHOT_CREATED,
                    "创建了快照", user, server, snapshot, snapshot.getId(),
                    "类型: " + type + ", 笔记: " + (request.note() != null ? request.note() : "无"));

            return SnapshotDTO.from(snapshot);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            // Log detailed error for debugging but don't expose internals to the client
            log.error("[SNAPSHOT_CREATE] [server={}] Snapshot engine failed: {}", request.serverId(), e.getMessage(), e);
            throw new BadRequestException("快照创建失败，请检查服务器连接和存储目标配置");
        }
    }

    @Transactional(readOnly = true)
    public List<SnapshotDiffDTO> getSnapshotDiff(Long snapshotId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        // Use paginated query to find only the previous snapshot instead of loading all
        List<Snapshot> previousSnapshots = snapshotRepository.findPreviousSnapshots(
                snapshot.getServer().getId(), snapshotId, PageRequest.of(0, 1));

        if (!previousSnapshots.isEmpty() && snapshot.getHash() != null) {
            try {
                Snapshot previous = previousSnapshots.get(0);

                if (previous.getHash() != null) {
                    SshConnection conn = sshManager.getConnection(snapshot.getServer());
                    StorageTarget target = getPreferredStorageTarget();
                    if (target != null) {
                        String repoUrl = resticClient.buildRepoUrl(target);
                        String diffOutput = resticClient.diff(conn, repoUrl, resticPassword,
                                previous.getHash(), snapshot.getHash());
                        // Parse diff output and convert to SnapshotDiffDTO
                        return parseDiffOutput(snapshot, diffOutput);
                    }
                }
            } catch (Exception e) {
                log.warn("[SNAPSHOT_DIFF] [snapshot={}] Failed to get real diff, falling back to DB: {}", snapshotId, e.getMessage());
            }
        }

        return snapshotDiffRepository.findBySnapshotId(snapshotId).stream()
                .map(SnapshotDiffDTO::from)
                .toList();
    }

    /**
     * Compare two snapshots and return diff with statistics.
     */
    @Transactional(readOnly = true)
    public SnapshotDiffDTO.DiffSummary compareSnapshots(Long fromId, Long toId) {
        Snapshot from = snapshotRepository.findById(fromId)
                .orElseThrow(() -> new ResourceNotFoundException("源快照不存在: " + fromId));
        Snapshot to = snapshotRepository.findById(toId)
                .orElseThrow(() -> new ResourceNotFoundException("目标快照不存在: " + toId));

        List<SnapshotDiffDTO> diffs = new java.util.ArrayList<>();

        if (from.getHash() != null && to.getHash() != null
                && from.getServer().getId().equals(to.getServer().getId())) {
            try {
                SshConnection conn = sshManager.getConnection(from.getServer());
                StorageTarget target = getPreferredStorageTarget();
                if (target != null) {
                    String repoUrl = resticClient.buildRepoUrl(target);
                    String diffOutput = resticClient.diff(conn, repoUrl, resticPassword,
                            from.getHash(), to.getHash());
                    diffs = parseDiffOutput(to, diffOutput);
                }
            } catch (Exception e) {
                log.warn("[SNAPSHOT_DIFF] [snapshot={}] Failed to compare snapshots {}→{}: {}", toId, fromId, toId, e.getMessage());
            }
        }

        int added = (int) diffs.stream().filter(d -> "added".equals(d.changeType())).count();
        int modified = (int) diffs.stream().filter(d -> "modified".equals(d.changeType())).count();
        int deleted = (int) diffs.stream().filter(d -> "deleted".equals(d.changeType())).count();

        return new SnapshotDiffDTO.DiffSummary(added, modified, deleted, diffs);
    }

    /**
     * Execute a full rollback — restores files via Restic over SSH.
     * Split into non-transactional SSH work + transactional DB updates
     * to avoid holding a DB transaction open during long network calls.
     */
    public void rollback(Long snapshotId, Long userId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        StorageTarget storageTarget = getAnyStorageTarget();

        // Non-transactional: SSH operations (can take minutes)
        boolean success;
        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());

            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法在目标服务器上安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            success = resticClient.restore(conn, repoUrl, resticPassword,
                    snapshot.getHash(), "/");
        } catch (Exception e) {
            log.error("[SNAPSHOT_ROLLBACK] [snapshot={}] SSH operation failed: {}", snapshotId, e.getMessage());
            updateRollbackStatus(snapshotId, Snapshot.SnapshotStatus.WARNING);
            throw new BadRequestException("回滚失败: " + e.getMessage());
        }

        // Transactional: Update DB status after SSH completes
        updateRollbackResult(snapshotId, success, userId);
    }

    @Transactional
    void updateRollbackStatus(Long snapshotId, Snapshot.SnapshotStatus status) {
        snapshotRepository.findById(snapshotId).ifPresent(s -> {
            s.setStatus(status);
            snapshotRepository.save(s);
        });
    }

    @Transactional
    void updateRollbackResult(Long snapshotId, boolean success, Long userId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (success) {
            snapshot.setStatus(Snapshot.SnapshotStatus.STABLE);
            User user = userRepository.findById(userId).orElse(null);
            attributionService.record(AuditLog.ChangeType.SNAPSHOT_RESTORED,
                    "回滚至快照 " + snapshot.getTitle(), user, snapshot.getServer(),
                    snapshot, snapshot.getId(), "执行了全量回滚");
        } else {
            snapshot.setStatus(Snapshot.SnapshotStatus.WARNING);
        }
        snapshotRepository.save(snapshot);
    }

    /**
     * Preview what a rollback would do without actually executing it.
     * Returns information about the target snapshot, server, and affected files.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> rollbackPreview(Long snapshotId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        Map<String, Object> preview = new java.util.LinkedHashMap<>();
        preview.put("snapshotId", snapshotId);
        preview.put("snapshotTitle", snapshot.getTitle());
        preview.put("serverName", snapshot.getServer() != null ? snapshot.getServer().getName() : "未知");
        preview.put("serverIp", snapshot.getServer() != null ? snapshot.getServer().getIp() : "未知");
        preview.put("hash", snapshot.getHash());
        preview.put("sizeBytes", snapshot.getSizeBytes());
        preview.put("createdAt", snapshot.getCreatedAt() != null ? snapshot.getCreatedAt().toString() : "未知");

        // Check if backup data is available
        boolean hasValidBackup = snapshot.getHash() != null && !snapshot.getHash().isBlank();
        preview.put("hasValidBackup", hasValidBackup);

        // Get the state.json summary if available
        if (snapshot.getChangeSummaryJson() != null) {
            preview.put("changeSummary", snapshot.getChangeSummaryJson());
        }

        // Find the server's storage target
        StorageTarget target = getPreferredStorageTarget();
        preview.put("storageAvailable", target != null);
        if (target != null) {
            preview.put("storageType", target.getType().name());
        }

        // Estimate restore time (rough: 1GB per minute)
        long sizeMB = (snapshot.getSizeBytes() != null ? snapshot.getSizeBytes() : 0) / (1024 * 1024);
        preview.put("estimatedRestoreTimeSeconds", Math.max(30, sizeMB / 17)); // ~17MB/sec

        return preview;
    }

    /**
     * Selectively rollback specific items (config files or package versions).
     * Items are a list of maps with "type" (config/package) and type-specific fields.
     */
    @Transactional
    public String selectiveRollback(Long snapshotId, List<Map<String, String>> items, Long userId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (snapshot.getHash() == null || snapshot.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据，无法执行选择性回滚");
        }

        if (items == null || items.isEmpty()) {
            throw new BadRequestException("请选择要回滚的项目");
        }

        StorageTarget storageTarget = getAnyStorageTarget();

        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法在目标服务器上安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            int restoredCount = 0;
            StringBuilder resultLog = new StringBuilder();

            for (Map<String, String> item : items) {
                String type = item.get("type");
                if ("config".equals(type)) {
                    // Restore a specific config file from the snapshot
                    String path = item.get("path");
                    if (path == null || path.isBlank()) continue;

                    String content = resticClient.dumpFile(conn, repoUrl, resticPassword,
                            snapshot.getHash(), path);
                    if (content != null && !content.isBlank()) {
                        // Write the file back to its original location
                        String writeCmd = String.format("echo %s | sudo tee %s > /dev/null",
                                shellEscapeForSsh(content), path);
                        SshConnection.CommandResult writeResult = conn.executeCommand(writeCmd,
                                java.time.Duration.ofSeconds(30));
                        if (writeResult.isSuccess()) {
                            restoredCount++;
                            resultLog.append("✓ 已恢复配置: ").append(path).append("\n");
                        } else {
                            resultLog.append("✗ 恢复失败: ").append(path).append(" - ").append(writeResult.stderr()).append("\n");
                        }
                    }
                } else if ("package".equals(type)) {
                    // Rollback a package to a specific version
                    String name = item.get("name");
                    String targetVersion = item.get("target_version");
                    if (name == null || name.isBlank()) continue;

                    String installCmd;
                    if (targetVersion != null && !targetVersion.isBlank()) {
                        installCmd = String.format("sudo apt-get install -y %s=%s 2>/dev/null || sudo yum install -y %s-%s 2>/dev/null",
                                name, targetVersion, name, targetVersion);
                    } else {
                        installCmd = String.format("sudo apt-get install -y --reinstall %s 2>/dev/null || sudo yum reinstall -y %s 2>/dev/null",
                                name, name);
                    }

                    SshConnection.CommandResult installResult = conn.executeCommand(installCmd,
                            java.time.Duration.ofMinutes(5));
                    if (installResult.isSuccess()) {
                        restoredCount++;
                        resultLog.append("✓ 已回滚包: ").append(name)
                                .append(targetVersion != null ? " → " + targetVersion : " (重装)").append("\n");
                    } else {
                        resultLog.append("✗ 回滚包失败: ").append(name).append(" - ").append(installResult.stderr()).append("\n");
                    }
                }
            }

            // Record the selective rollback
            User user = userRepository.findById(userId).orElse(null);
            attributionService.record(AuditLog.ChangeType.SNAPSHOT_RESTORED,
                    "选择性回滚 " + restoredCount + " 个项目", user, snapshot.getServer(),
                    snapshot, snapshot.getId(), resultLog.toString());

            return "选择性回滚完成，成功恢复 " + restoredCount + "/" + items.size() + " 个项目\n" + resultLog;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SNAPSHOT_ROLLBACK] [snapshot={}] Selective rollback failed: {}", snapshotId, e.getMessage(), e);
            throw new RollbackFailedException("选择性回滚失败，请检查服务器连接状态和备份数据完整性", e);
        }
    }

    /** Escape content for safe use in SSH shell commands */
    private String shellEscapeForSsh(String content) {
        if (content == null) return "";
        return "'" + content.replace("'", "'\\''").replace("\\", "\\\\") + "'";
    }

    @Transactional
    public String revert(Long snapshotId, Long userId) {
        Snapshot target = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (target.getHash() == null || target.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据（hash 为空），无法撤销");
        }

        // Find the snapshot created just before this one (the "parent") — use paginated query
        List<Snapshot> previousSnapshots = snapshotRepository.findPreviousSnapshots(
                target.getServer().getId(), snapshotId, PageRequest.of(0, 1));
        Snapshot parent = previousSnapshots.isEmpty() ? null : previousSnapshots.get(0);

        if (parent == null) {
            throw new BadRequestException("没有找到此快照之前的快照，无法执行撤销操作");
        }

        StorageTarget storageTarget = getAnyStorageTarget();

        try {
            SshConnection conn = sshManager.getConnection(target.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法在目标服务器上安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(storageTarget);

            // Step 1: Create a "pre-revert" safety snapshot
            log.info("[SNAPSHOT_REVERT] [snapshot={}] Creating pre-revert safety snapshot", snapshotId);
            Snapshot safetySnapshot = snapshotEngine.createSnapshot(target.getServer(), storageTarget,
                    "撤销前安全快照 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    "在撤销快照 #" + snapshotId + " 之前自动创建", Snapshot.SnapshotType.FULL, userId,
                    null, null);

            // Step 2: Get diff between parent and target to find changed files
            String diffOutput = resticClient.diff(conn, repoUrl, resticPassword,
                    parent.getHash(), target.getHash());

            List<String> changedPaths = parseChangedPaths(diffOutput);
            if (changedPaths.isEmpty()) {
                throw new BadRequestException("此快照与前一快照之间没有发现文件变更");
            }

            // Step 3: Restore parent snapshot's files to server (undoing target's changes)
            log.info("[SNAPSHOT_REVERT] [snapshot={}] Restoring {} files from parent snapshot {}",
                    snapshotId, changedPaths.size(), parent.getId());
            boolean success = resticClient.restoreToServer(conn, repoUrl, resticPassword,
                    parent.getHash(), changedPaths);

            if (!success) {
                throw new BadRequestException("文件恢复失败，撤销操作中止。安全快照已创建，可手动回滚");
            }

            // Step 4: Create a new snapshot capturing the reverted state
            Snapshot revertedSnapshot = snapshotEngine.createSnapshot(target.getServer(), storageTarget,
                    "撤销快照 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    "已撤销快照 #" + snapshotId + " 的变更（" + changedPaths.size() + " 个文件）",
                    Snapshot.SnapshotType.FULL, userId, null, null);

            return "已撤销快照 \"" + target.getTitle() + "\" 的变更，恢复了 " + changedPaths.size()
                    + " 个文件。已创建安全快照 #" + safetySnapshot.getId()
                    + " 和撤销快照 #" + revertedSnapshot.getId();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SNAPSHOT_REVERT] [snapshot={}] Revert failed: {}", snapshotId, e.getMessage(), e);
            throw new RollbackFailedException("撤销操作失败，请检查服务器连接状态和备份数据完整性", e);
        }
    }

    /**
     * Parse restic diff output to extract the list of file paths that changed.
     */
    private List<String> parseChangedPaths(String diffOutput) {
        List<String> paths = new java.util.ArrayList<>();
        if (diffOutput == null || diffOutput.isBlank()) return paths;

        for (String line : diffOutput.lines().toList()) {
            if (line.isBlank()) continue;
            // Restic diff format: "changed   /path/to/file"
            // or "added     /path/to/file"
            // or "removed   /path/to/file"
            String trimmed = line.stripLeading();
            int spaceIdx = trimmed.indexOf(' ');
            if (spaceIdx > 0 && spaceIdx < 12) {
                String path = trimmed.substring(spaceIdx + 1).trim();
                if (!path.isEmpty() && !path.equals("/")) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    @Transactional
    public String restoreFiles(Long snapshotId, SelectiveRestoreRequest request) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (snapshot.getHash() == null || snapshot.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据（hash 为空），无法恢复文件");
        }

        StorageTarget storageTarget = getAnyStorageTarget();

        String targetPath = request.targetPath() != null && !request.targetPath().isBlank()
                ? request.targetPath()
                : "/var/chronovault/restore/" + snapshotId + "/";

        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());

            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法在目标服务器上安装 restic 备份工具");
            }

            // Ensure target directory exists
            conn.executeCommand("sudo mkdir -p " + targetPath + " && sudo chown $(whoami) " + targetPath + " 2>/dev/null");

            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            boolean success = resticClient.restoreSelective(conn, repoUrl, resticPassword,
                    snapshot.getHash(), request.paths(), targetPath);

            if (!success) {
                throw new BadRequestException("文件恢复失败，请检查快照数据和目标路径权限");
            }

            return "已恢复 " + request.paths().size() + " 个文件/目录到 " + targetPath;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SNAPSHOT_RESTORE] [snapshot={}] File restore failed: {}", snapshotId, e.getMessage(), e);
            throw new RollbackFailedException("文件恢复失败，请检查快照数据和服务器连接状态", e);
        }
    }

    @Transactional
    public String cherryPick(Long snapshotId, CherryPickRequest request, Long userId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (snapshot.getHash() == null || snapshot.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据，无法进行 Cherry-pick");
        }

        Server targetServer = serverRepository.findById(request.targetServerId())
                .orElseThrow(() -> new ResourceNotFoundException("目标服务器不存在: " + request.targetServerId()));

        StorageTarget storageTarget = getAnyStorageTarget();

        try {
            // Step 1: Extract files from snapshot to temp dir on source server
            SshConnection sourceConn = sshManager.getConnection(snapshot.getServer());
            if (!resticClient.ensureResticInstalled(sourceConn)) {
                throw new BadRequestException("无法安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            String tempDir = "/var/chronovault/cherry-pick-" + snapshotId + "-" + System.currentTimeMillis() + "/";

            log.info("[SNAPSHOT_CHERRY_PICK] [snapshot={}] Extracting {} files to temp dir", snapshotId, request.files().size());
            boolean dumpOk = resticClient.dumpFiles(sourceConn, repoUrl, resticPassword,
                    snapshot.getHash(), request.files(), tempDir);

            if (!dumpOk) {
                throw new BadRequestException("从快照中提取文件失败");
            }

            // Step 2: Copy files to target server
            log.info("[SNAPSHOT_CHERRY_PICK] [snapshot={}] Copying files to target server {}", snapshotId, targetServer.getIp());
            SshConnection targetConn = sshManager.getConnection(targetServer);
            if (!resticClient.ensureResticInstalled(targetConn)) {
                throw new BadRequestException("目标服务器无法安装 restic");
            }

            // Copy each file to its original path on the target
            StringBuilder copyCmd = new StringBuilder();
            copyCmd.append("sudo mkdir -p /var/chronovault/cherry-pick-tmp && ");
            for (String file : request.files()) {
                // Use scp-style approach: dump from source, pipe to target
                // Since both servers share the same restic repo, we can restore directly on target
                String restoreCmd = String.format(
                        "RESTIC_PASSWORD=%s %s restore %s --include %s --target / --repo %s 2>&1",
                        resticPassword, resticClient.getResticPath(targetConn),
                        snapshot.getHash(), file, repoUrl);
                SshConnection.CommandResult restoreResult = targetConn.executeCommand(restoreCmd,
                        java.time.Duration.ofMinutes(30));
                if (!restoreResult.isSuccess()) {
                    log.warn("[SNAPSHOT_CHERRY_PICK] [snapshot={}] Failed to restore {} on target: {}", snapshotId, file,
                            restoreResult.stderr() != null ? restoreResult.stderr().substring(0, Math.min(200, restoreResult.stderr().length())) : "");
                }
            }

            // Step 3: Cleanup temp dir on source
            try {
                sourceConn.executeCommand("sudo rm -rf " + tempDir + " 2>/dev/null");
            } catch (Exception e) {
                log.warn("[SNAPSHOT_CHERRY_PICK] [snapshot={}] Failed to cleanup temp dir: {}", snapshotId, e.getMessage());
            }

            // Record blame
            User user = userRepository.findById(userId).orElse(null);
            attributionService.record(AuditLog.ChangeType.SNAPSHOT_CREATED,
                    "Cherry-pick " + request.files().size() + " 个文件到 " + targetServer.getName(),
                    user, snapshot.getServer(), snapshot, snapshotId,
                    "文件: " + String.join(", ", request.files()));

            return "已将 " + request.files().size() + " 个文件从快照 \"" + snapshot.getTitle()
                    + "\" 应用到服务器 \"" + targetServer.getName() + "\"";
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SNAPSHOT_CHERRY_PICK] [snapshot={}] Cherry-pick failed: {}", snapshotId, e.getMessage(), e);
            throw new RollbackFailedException("Cherry-pick 操作失败，请检查服务器连接状态和备份数据完整性", e);
        }
    }

    @Transactional(readOnly = true)
    public List<SnapshotFileEntry> listSnapshotFiles(Long snapshotId, String path) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (snapshot.getHash() == null || snapshot.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据");
        }

        StorageTarget storageTarget = getAnyStorageTarget();

        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法安装 restic");
            }

            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            String lsOutput = resticClient.listFiles(conn, repoUrl, resticPassword,
                    snapshot.getHash(), path);

            return parseLsOutput(lsOutput, path);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SNAPSHOT_LIST] [snapshot={}] Failed to list files: {}", snapshotId, e.getMessage(), e);
            throw new BadRequestException("文件列表获取失败，请检查快照数据完整性");
        }
    }

    @Transactional(readOnly = true)
    public String getSnapshotFileContent(Long snapshotId, String filePath) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (snapshot.getHash() == null || snapshot.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据");
        }

        StorageTarget storageTarget = getAnyStorageTarget();

        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法安装 restic");
            }

            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            return resticClient.dumpFile(conn, repoUrl, resticPassword,
                    snapshot.getHash(), filePath);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SNAPSHOT_DUMP] [snapshot={}] Failed to dump file {}: {}", snapshotId, filePath, e.getMessage(), e);
            throw new BadRequestException("文件内容获取失败，请检查快照数据完整性");
        }
    }

    private List<SnapshotFileEntry> parseLsOutput(String lsOutput, String currentPath) {
        List<SnapshotFileEntry> entries = new java.util.ArrayList<>();
        if (lsOutput == null || lsOutput.isBlank()) return entries;

        String prefix = (currentPath != null && !currentPath.isBlank()) ? currentPath : "";
        if (!prefix.endsWith("/")) prefix += "/";

        for (String line : lsOutput.lines().toList()) {
            if (line.isBlank() || line.startsWith("repository") || line.startsWith("created")
                    || line.startsWith("snapshot") || line.startsWith("tree")) continue;

            // Restic ls output format: "type    size    path"
            // e.g. "dir     0       /etc/nginx"
            // or   "file    1234    /etc/nginx/nginx.conf"
            String trimmed = line.stripLeading();
            String[] parts = trimmed.split("\\s+", 3);
            if (parts.length < 3) continue;

            String type = parts[0];
            String sizeStr = parts[1];
            String fullPath = parts[2];

            // Skip the root entry itself
            if (fullPath.equals("/") || fullPath.equals(currentPath)) continue;

            long size = 0;
            try { size = Long.parseLong(sizeStr); } catch (NumberFormatException ignored) {}

            String name = fullPath.contains("/") ? fullPath.substring(fullPath.lastIndexOf('/') + 1) : fullPath;
            if (name.isEmpty()) name = fullPath;

            String entryType = "dir".equals(type) ? "DIRECTORY" : "FILE";
            entries.add(new SnapshotFileEntry(fullPath, name, size, entryType, ""));
        }

        return entries;
    }

    @Transactional
    public SnapshotVerifyResult verifySnapshot(Long snapshotId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (snapshot.getHash() == null || snapshot.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据");
        }

        StorageTarget storageTarget = getAnyStorageTarget();

        long startTime = System.currentTimeMillis();
        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法安装 restic");
            }

            String repoUrl = resticClient.buildRepoUrl(storageTarget);
            boolean ok = resticClient.check(conn, repoUrl, resticPassword);
            long duration = System.currentTimeMillis() - startTime;

            snapshot.setVerified(true);
            snapshot.setVerifiedAt(LocalDateTime.now());
            if (ok) {
                snapshot.setStatus(Snapshot.SnapshotStatus.STABLE);
            }
            snapshotRepository.save(snapshot);

            return new SnapshotVerifyResult(snapshotId, ok, ok ? null : "完整性检查发现问题", duration);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[SNAPSHOT_VERIFY] [snapshot={}] Verification failed: {}", snapshotId, e.getMessage());
            return new SnapshotVerifyResult(snapshotId, false, e.getMessage(), duration);
        }
    }

    /**
     * Verify oldest unverified snapshot daily at 4:00 AM.
     * Uses a paginated query to avoid loading all snapshots into memory.
     */
    @Scheduled(cron = "0 0 4 * * ?")
    @Transactional
    public void scheduledVerification() {
        // Use dedicated repository method to avoid loading all snapshots
        PageRequest pageRequest = PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "createdAt"));
        List<Snapshot> unverified = snapshotRepository.findUnverifiedUnstashed(pageRequest);

        log.info("[SNAPSHOT_VERIFY] [scheduled] Verifying {} unverified snapshots", unverified.size());
        for (Snapshot snapshot : unverified) {
            try {
                verifySnapshot(snapshot.getId());
            } catch (Exception e) {
                log.warn("[SNAPSHOT_VERIFY] [snapshot={}] Scheduled verification failed: {}", snapshot.getId(), e.getMessage());
            }
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
                String changeType = "removed".equals(action) ? "deleted" : "added".equals(action) ? "added" : "modified";
                diffs.add(new SnapshotDiffDTO(path,
                        "removed".equals(action) ? "deleted" : null,
                        "added".equals(action) ? "created" : "modified",
                        changeType));
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
        // 1. Delete all null-hash snapshots from DB (useless records) — use targeted query
        List<Snapshot> nullHashSnapshots = snapshotRepository.findNullHashSnapshots();
        int deletedCount = nullHashSnapshots.size();
        snapshotRepository.deleteAll(nullHashSnapshots);
        log.info("[SNAPSHOT_CLEANUP] Deleted {} null-hash snapshot records from DB", deletedCount);

        // 2. Delete restic repository on remote server
        StorageTarget localTarget = storageTargetRepository.findByType(StorageTarget.StorageType.LOCAL).stream()
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
                        log.info("[SNAPSHOT_CLEANUP] Deleted local restic repo at {} (was {})", repoPath, sizeBefore);
                    } else {
                        result += "，删除本地仓库失败: " + deleteResult.stderr();
                        log.warn("[SNAPSHOT_CLEANUP] Failed to delete local repo: {}", deleteResult.stderr());
                    }
                }
            } catch (Exception e) {
                log.error("[SNAPSHOT_CLEANUP] Failed to cleanup local repo: {}", e.getMessage());
                result += "，清理本地仓库时出错: " + e.getMessage();
            }
        }

        return result;
    }

    // ===== State.json methods (P0-4) =====

    /**
     * Get paginated snapshots for timeline view with change summaries included.
     * Uses database-level pagination via Pageable instead of loading all and filtering in Java.
     */
    @Transactional(readOnly = true)
    public List<SnapshotDTO> getSnapshotsForTimeline(Long serverId, int page, int size, String sort, String direction) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = (sort != null && !sort.isBlank()) ? sort : "createdAt";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(dir, sortField));
        return snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId, pageable).stream()
                .map(s -> {
                    List<SnapshotTagDTO> tags = tagRepository.findBySnapshotIdOrderByCreatedAtDesc(s.getId())
                            .stream().map(SnapshotTagDTO::from).toList();
                    return SnapshotDTO.fromDetail(s, tags);
                })
                .toList();
    }

    /**
     * Get the state.json content for a snapshot.
     * Returns the raw JSON string captured by the agent during snapshot creation.
     */
    @Transactional(readOnly = true)
    public String getStateSnapshot(Long snapshotId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));
        return snapshot.getStateJson();
    }

    /**
     * Get the pre-computed change summary for a snapshot (relative to the previous snapshot).
     * This avoids real-time diff computation for the timeline view.
     */
    @Transactional(readOnly = true)
    public String getChangeSummary(Long snapshotId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));
        return snapshot.getChangeSummaryJson();
    }

    /**
     * Compute a full state diff between two snapshots using the StateDiffEngine.
     * Returns the diff result as a JSON string.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> computeStateDiff(Long fromId, Long toId) {
        Snapshot from = snapshotRepository.findById(fromId)
                .orElseThrow(() -> new ResourceNotFoundException("源快照不存在: " + fromId));
        Snapshot to = snapshotRepository.findById(toId)
                .orElseThrow(() -> new ResourceNotFoundException("目标快照不存在: " + toId));

        String stateA = from.getStateJson();
        String stateB = to.getStateJson();

        StateDiffEngine.StateDiffResult diffResult = stateDiffEngine.diff(stateA, stateB);
        if (diffResult == null) {
            diffResult = StateDiffEngine.StateDiffResult.empty();
        }

        // Convert to a Map for JSON serialization
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("snapshot_a", fromId);
        result.put("snapshot_b", toId);

        if (diffResult.summary() != null) {
            Map<String, Object> summary = new java.util.LinkedHashMap<>();
            summary.put("packages_added", diffResult.summary().packagesAdded);
            summary.put("packages_removed", diffResult.summary().packagesRemoved);
            summary.put("packages_upgraded", diffResult.summary().packagesUpgraded);
            summary.put("services_changed", diffResult.summary().servicesChanged);
            summary.put("ports_changed", diffResult.summary().portsChanged);
            summary.put("docker_changed", diffResult.summary().dockerChanged);
            summary.put("configs_changed", diffResult.summary().configsChanged);
            summary.put("crontab_changed", diffResult.summary().crontabChanged);
            result.put("summary", summary);
        }

        if (diffResult.packages() != null) {
            Map<String, Object> packages = new java.util.LinkedHashMap<>();
            packages.put("added", diffResult.packages().added);
            packages.put("removed", diffResult.packages().removed);
            packages.put("upgraded", diffResult.packages().upgraded);
            result.put("packages", packages);
        }

        if (diffResult.services() != null) {
            Map<String, Object> services = new java.util.LinkedHashMap<>();
            services.put("added", diffResult.services().added);
            services.put("removed", diffResult.services().removed);
            services.put("changed", diffResult.services().changed);
            result.put("services", services);
        }

        if (diffResult.ports() != null) {
            Map<String, Object> ports = new java.util.LinkedHashMap<>();
            ports.put("added", diffResult.ports().added);
            ports.put("removed", diffResult.ports().removed);
            result.put("ports", ports);
        }

        if (diffResult.docker() != null) {
            Map<String, Object> docker = new java.util.LinkedHashMap<>();
            docker.put("containers_added", diffResult.docker().containersAdded);
            docker.put("containers_removed", diffResult.docker().containersRemoved);
            docker.put("containers_changed", diffResult.docker().containersChanged);
            result.put("docker", docker);
        }

        if (diffResult.configs() != null) {
            Map<String, Object> configs = new java.util.LinkedHashMap<>();
            configs.put("added", diffResult.configs().added);
            configs.put("removed", diffResult.configs().removed);
            configs.put("changed", diffResult.configs().changed);
            result.put("configs", configs);
        }

        if (diffResult.crontab() != null) {
            Map<String, Object> crontab = new java.util.LinkedHashMap<>();
            crontab.put("added", diffResult.crontab().added);
            crontab.put("removed", diffResult.crontab().removed);
            result.put("crontab", crontab);
        }

        return result;
    }

    /**
     * Compute and cache the change summary for a snapshot relative to its previous snapshot.
     * Called after snapshot creation to pre-compute the summary for the timeline view.
     */
    @Transactional
    public void computeAndCacheChangeSummary(Snapshot snapshot) {
        if (snapshot.getStateJson() == null) return;

        // Find the previous snapshot for this server
        List<Snapshot> previousSnapshots = snapshotRepository
                .findByServerIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                        snapshot.getServer().getId(), snapshot.getCreatedAt());

        if (previousSnapshots.isEmpty()) return;

        Snapshot previous = previousSnapshots.get(previousSnapshots.size() - 1);
        if (previous.getStateJson() == null) return;

        StateDiffEngine.StateDiffResult diffResult = stateDiffEngine.diff(
                previous.getStateJson(), snapshot.getStateJson());

        // Serialize the summary as JSON
        try {
            Map<String, Object> summary = new java.util.LinkedHashMap<>();
            summary.put("packages_added", diffResult.summary().packagesAdded);
            summary.put("packages_removed", diffResult.summary().packagesRemoved);
            summary.put("packages_upgraded", diffResult.summary().packagesUpgraded);
            summary.put("services_changed", diffResult.summary().servicesChanged);
            summary.put("ports_changed", diffResult.summary().portsChanged);
            summary.put("configs_changed", diffResult.summary().configsChanged);
            summary.put("os_changed", diffResult.summary().osChanged);
            summary.put("kernel_changed", diffResult.summary().kernelChanged);

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            snapshot.setChangeSummaryJson(mapper.writeValueAsString(summary));
            snapshot.setPreviousSnapshot(previous);
            snapshotRepository.save(snapshot);
        } catch (Exception e) {
            log.warn("[SNAPSHOT_SUMMARY] [snapshot={}] Failed to cache change summary: {}", snapshot.getId(), e.getMessage());
        }
    }

    /**
     * Detect high-risk changes between two snapshots and create alerts if found.
     * Called after snapshot creation to auto-generate security alerts.
     *
     * High-risk changes:
     * - New ports opened (especially 22/3306/5432/6379)
     * - Service disabled (enabled → disabled)
     * - /etc/hosts or /etc/sudoers changed
     * - Docker image version downgrade
     */
    @Transactional
    public void detectAndAlertHighRiskChanges(Snapshot previousSnapshot, Snapshot currentSnapshot) {
        if (previousSnapshot.getStateJson() == null || currentSnapshot.getStateJson() == null) return;

        try {
            StateDiffEngine.StateDiffResult diff = stateDiffEngine.diff(
                    previousSnapshot.getStateJson(), currentSnapshot.getStateJson());

            List<String> riskReasons = new java.util.ArrayList<>();

            // Check for new high-risk ports
            if (diff.ports() != null) {
                Set<Integer> highRiskPorts = Set.of(22, 23, 3306, 5432, 6379, 27017, 9200);
                for (String portEntry : diff.ports().added) {
                    // Parse "port/protocol" format
                    String portStr = portEntry.split("/")[0];
                    try {
                        int port = Integer.parseInt(portStr);
                        if (highRiskPorts.contains(port)) {
                            riskReasons.add("新开放高风险端口: " + portEntry);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            // Check for services that became disabled
            if (diff.services() != null) {
                for (StateDiffEngine.ServiceChange change : diff.services().changed) {
                    if (change.fromEnabled && !change.toEnabled) {
                        riskReasons.add("服务被禁用: " + change.name);
                    }
                }
            }

            // Check for critical config changes
            if (diff.configs() != null) {
                for (String changedPath : diff.configs().changed) {
                    if (changedPath.contains("/etc/hosts") || changedPath.contains("/etc/sudoers")
                            || changedPath.contains("/etc/passwd") || changedPath.contains("/etc/shadow")
                            || changedPath.contains("/etc/ssh/sshd_config")) {
                        riskReasons.add("关键配置文件变更: " + changedPath);
                    }
                }
            }

            // Create alert if high-risk changes detected
            if (!riskReasons.isEmpty()) {
                Alert alert = Alert.builder()
                        .server(currentSnapshot.getServer())
                        .severity(Alert.AlertSeverity.WARNING)
                        .title("检测到 " + riskReasons.size() + " 项高风险变更")
                        .description(String.join("\n", riskReasons))
                        .source("snapshot-diff")
                        .category("安全变更")
                        .status(Alert.AlertStatus.OPEN)
                        .build();
                alertRepository.save(alert);
                log.warn("[SNAPSHOT_ALERT] [snapshot={}] Created high-risk alert: {} reasons",
                        currentSnapshot.getId(), riskReasons.size());

                // Send notification to configured channels
                try {
                    User owner = currentSnapshot.getServer() != null ? currentSnapshot.getServer().getUser() : null;
                    if (owner != null) {
                        notificationService.sendAlertNotification(alert, owner.getId());
                    }
                } catch (Exception notifEx) {
                    log.warn("[SNAPSHOT_ALERT] [snapshot={}] Failed to send alert notification: {}", currentSnapshot.getId(), notifEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[SNAPSHOT_ALERT] [snapshot={}] Failed to detect high-risk changes: {}",
                    currentSnapshot.getId(), e.getMessage());
        }
    }

    /**
     * Analyze the impact of a snapshot: which files, services, and configs were affected.
     * Compares with the previous snapshot to determine changes.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSnapshotImpact(Long snapshotId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        Map<String, Object> impact = new java.util.HashMap<>();
        impact.put("snapshotId", snapshotId);
        impact.put("createdAt", snapshot.getCreatedAt() != null ? snapshot.getCreatedAt().toString() : null);
        impact.put("serverId", snapshot.getServer() != null ? snapshot.getServer().getId() : null);

        // If no state.json, return basic info
        if (snapshot.getStateJson() == null || snapshot.getStateJson().isBlank()) {
            impact.put("summary", Map.of("packages", 0, "services", 0, "configs", 0, "containers", 0));
            return impact;
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> state = (Map<String, Object>) mapper.readValue(snapshot.getStateJson(), Object.class);

            // Count affected items from current state
            Map<String, Object> affected = new java.util.HashMap<>();

            // Packages
            Object packages = state.get("packages");
            if (packages instanceof List<?> pkgList) {
                affected.put("packages", pkgList.size());
                affected.put("packageNames", pkgList.stream()
                    .filter(p -> p instanceof Map)
                    .map(p -> ((Map<?, ?>) p).get("name"))
                    .filter(n -> n != null)
                    .limit(20)
                    .toList());
            }

            // Services
            Object services = state.get("services");
            if (services instanceof List<?> svcList) {
                affected.put("services", svcList.size());
                affected.put("serviceNames", svcList.stream()
                    .filter(s -> s instanceof Map)
                    .map(s -> ((Map<?, ?>) s).get("name"))
                    .filter(n -> n != null)
                    .limit(20)
                    .toList());
            }

            // Configs
            Object configs = state.get("configs");
            if (configs instanceof List<?> cfgList) {
                affected.put("configs", cfgList.size());
                affected.put("configPaths", cfgList.stream()
                    .filter(c -> c instanceof Map)
                    .map(c -> ((Map<?, ?>) c).get("path"))
                    .filter(p -> p != null)
                    .limit(20)
                    .toList());
            }

            // Docker containers
            Object docker = state.get("docker");
            if (docker instanceof Map<?, ?> dockerMap) {
                Object containers = dockerMap.get("containers");
                if (containers instanceof List<?> ctrList) {
                    affected.put("containers", ctrList.size());
                }
            }

            impact.put("affected", affected);
            impact.put("changeSummary", snapshot.getChangeSummaryJson());
        } catch (Exception e) {
            log.warn("[SNAPSHOT_IMPACT] Failed to parse state.json for snapshot {}: {}", snapshotId, e.getMessage());
            impact.put("error", "Failed to parse state data");
        }

        return impact;
    }
}
