package com.chronovault.service;

import com.chronovault.dto.snapshot.CherryPickRequest;
import com.chronovault.dto.snapshot.CreateSnapshotRequest;
import com.chronovault.dto.snapshot.SelectiveRestoreRequest;
import com.chronovault.dto.snapshot.SnapshotVerifyResult;
import com.chronovault.dto.snapshot.SnapshotFileEntry;
import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotDiffDTO;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.entity.AuditLog;
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
import org.springframework.scheduling.annotation.Scheduled;
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
    private final ChangeAttributionService attributionService;

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
    public List<SnapshotDTO> getSnapshotsByTag(String tagName) {
        List<Snapshot> allSnapshots = snapshotRepository.findAll();
        return allSnapshots.stream()
                .filter(s -> tagRepository.findBySnapshotIdAndName(s.getId(), tagName).isPresent())
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
                    request.note(), type, userId,
                    request.paths(), request.excludes());
            log.info("Snapshot created: id={}", snapshot.getId());

            // Record blame
            User user = userRepository.findById(userId).orElse(null);
            attributionService.record(AuditLog.ChangeType.SNAPSHOT_CREATED,
                    "创建了快照", user, server, snapshot, snapshot.getId(),
                    "类型: " + type + ", 笔记: " + (request.note() != null ? request.note() : "无"));

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
                List<StorageTarget> targets = storageTargetRepository.findAll();
                if (!targets.isEmpty()) {
                    String repoUrl = resticClient.buildRepoUrl(targets.get(0));
                    String diffOutput = resticClient.diff(conn, repoUrl, resticPassword,
                            from.getHash(), to.getHash());
                    diffs = parseDiffOutput(to, diffOutput);
                }
            } catch (Exception e) {
                log.warn("Failed to compare snapshots: {}", e.getMessage());
            }
        }

        int added = (int) diffs.stream().filter(d -> "added".equals(d.changeType())).count();
        int modified = (int) diffs.stream().filter(d -> "modified".equals(d.changeType())).count();
        int deleted = (int) diffs.stream().filter(d -> "deleted".equals(d.changeType())).count();

        return new SnapshotDiffDTO.DiffSummary(added, modified, deleted, diffs);
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
                // Record blame
                User user = userRepository.findById(userId).orElse(null);
                attributionService.record(AuditLog.ChangeType.SNAPSHOT_RESTORED,
                        "回滚至快照 " + snapshot.getTitle(), user, snapshot.getServer(),
                        snapshot, snapshot.getId(), "执行了全量回滚");
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

    @Transactional
    public String revert(Long snapshotId, Long userId) {
        Snapshot target = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (target.getHash() == null || target.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据（hash 为空），无法撤销");
        }

        // Find the snapshot created just before this one (the "parent")
        List<Snapshot> allSnapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(
                target.getServer().getId());
        Snapshot parent = allSnapshots.stream()
                .filter(s -> s.getCreatedAt().isBefore(target.getCreatedAt()) && s.getHash() != null)
                .findFirst()
                .orElse(null);

        if (parent == null) {
            throw new BadRequestException("没有找到此快照之前的快照，无法执行撤销操作");
        }

        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }

        try {
            SshConnection conn = sshManager.getConnection(target.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法在目标服务器上安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));

            // Step 1: Create a "pre-revert" safety snapshot
            log.info("Creating pre-revert safety snapshot before reverting snapshot {}", snapshotId);
            Snapshot safetySnapshot = snapshotEngine.createSnapshot(target.getServer(), targets.get(0),
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
            log.info("Restoring {} files from parent snapshot {} to undo changes from snapshot {}",
                    changedPaths.size(), parent.getId(), snapshotId);
            boolean success = resticClient.restoreToServer(conn, repoUrl, resticPassword,
                    parent.getHash(), changedPaths);

            if (!success) {
                throw new BadRequestException("文件恢复失败，撤销操作中止。安全快照已创建，可手动回滚");
            }

            // Step 4: Create a new snapshot capturing the reverted state
            Snapshot revertedSnapshot = snapshotEngine.createSnapshot(target.getServer(), targets.get(0),
                    "撤销快照 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    "已撤销快照 #" + snapshotId + " 的变更（" + changedPaths.size() + " 个文件）",
                    Snapshot.SnapshotType.FULL, userId, null, null);

            return "已撤销快照 \"" + target.getTitle() + "\" 的变更，恢复了 " + changedPaths.size()
                    + " 个文件。已创建安全快照 #" + safetySnapshot.getId()
                    + " 和撤销快照 #" + revertedSnapshot.getId();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Revert failed for snapshot {}: {}", snapshotId, e.getMessage(), e);
            throw new RuntimeException("撤销失败: " + e.getMessage(), e);
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

        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }

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

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
            boolean success = resticClient.restoreSelective(conn, repoUrl, resticPassword,
                    snapshot.getHash(), request.paths(), targetPath);

            if (!success) {
                throw new BadRequestException("文件恢复失败，请检查快照数据和目标路径权限");
            }

            return "已恢复 " + request.paths().size() + " 个文件/目录到 " + targetPath;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Selective restore failed for snapshot {}: {}", snapshotId, e.getMessage(), e);
            throw new RuntimeException("文件恢复失败: " + e.getMessage(), e);
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

        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }

        try {
            // Step 1: Extract files from snapshot to temp dir on source server
            SshConnection sourceConn = sshManager.getConnection(snapshot.getServer());
            if (!resticClient.ensureResticInstalled(sourceConn)) {
                throw new BadRequestException("无法安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
            String tempDir = "/var/chronovault/cherry-pick-" + snapshotId + "-" + System.currentTimeMillis() + "/";

            log.info("Extracting {} files from snapshot {} to temp dir {}", request.files().size(), snapshotId, tempDir);
            boolean dumpOk = resticClient.dumpFiles(sourceConn, repoUrl, resticPassword,
                    snapshot.getHash(), request.files(), tempDir);

            if (!dumpOk) {
                throw new BadRequestException("从快照中提取文件失败");
            }

            // Step 2: Copy files to target server
            log.info("Copying files to target server {}", targetServer.getIp());
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
                    log.warn("Failed to restore {} on target: {}", file,
                            restoreResult.stderr() != null ? restoreResult.stderr().substring(0, Math.min(200, restoreResult.stderr().length())) : "");
                }
            }

            // Step 3: Cleanup temp dir on source
            try {
                sourceConn.executeCommand("sudo rm -rf " + tempDir + " 2>/dev/null");
            } catch (Exception e) {
                log.warn("Failed to cleanup temp dir: {}", e.getMessage());
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
            log.error("Cherry-pick failed for snapshot {}: {}", snapshotId, e.getMessage(), e);
            throw new RuntimeException("Cherry-pick 失败: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<SnapshotFileEntry> listSnapshotFiles(Long snapshotId, String path) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (snapshot.getHash() == null || snapshot.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据");
        }

        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }

        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法安装 restic");
            }

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
            String lsOutput = resticClient.listFiles(conn, repoUrl, resticPassword,
                    snapshot.getHash(), path);

            return parseLsOutput(lsOutput, path);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to list files for snapshot {}: {}", snapshotId, e.getMessage());
            throw new RuntimeException("文件列表获取失败: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public String getSnapshotFileContent(Long snapshotId, String filePath) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        if (snapshot.getHash() == null || snapshot.getHash().isBlank()) {
            throw new BadRequestException("快照没有有效的备份数据");
        }

        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }

        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法安装 restic");
            }

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
            return resticClient.dumpFile(conn, repoUrl, resticPassword,
                    snapshot.getHash(), filePath);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to dump file {} from snapshot {}: {}", filePath, snapshotId, e.getMessage());
            throw new RuntimeException("文件内容获取失败: " + e.getMessage(), e);
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

        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }

        long startTime = System.currentTimeMillis();
        try {
            SshConnection conn = sshManager.getConnection(snapshot.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法安装 restic");
            }

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
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
            log.error("Verification failed for snapshot {}: {}", snapshotId, e.getMessage());
            return new SnapshotVerifyResult(snapshotId, false, e.getMessage(), duration);
        }
    }

    /**
     * Verify oldest unverified snapshot daily at 4:00 AM.
     */
    @Scheduled(cron = "0 0 4 * * ?")
    @Transactional
    public void scheduledVerification() {
        List<Snapshot> unverified = snapshotRepository.findAll().stream()
                .filter(s -> !s.isVerified() && s.getHash() != null && !s.getHash().isBlank())
                .filter(s -> s.getType() != Snapshot.SnapshotType.STASH)
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .limit(5)
                .toList();

        for (Snapshot snapshot : unverified) {
            try {
                log.info("Scheduled verification of snapshot {} ({})", snapshot.getId(), snapshot.getTitle());
                verifySnapshot(snapshot.getId());
            } catch (Exception e) {
                log.warn("Scheduled verification failed for snapshot {}: {}", snapshot.getId(), e.getMessage());
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
