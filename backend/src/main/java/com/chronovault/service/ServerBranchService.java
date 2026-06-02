package com.chronovault.service;

import com.chronovault.dto.branch.CreateBranchRequest;
import com.chronovault.dto.branch.MergeBranchRequest;
import com.chronovault.dto.branch.ServerBranchDTO;
import com.chronovault.entity.Server;
import com.chronovault.entity.ServerBranch;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerBranchRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerBranchService {

    private final ServerBranchRepository branchRepository;
    private final ServerRepository serverRepository;
    private final SnapshotRepository snapshotRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    @Transactional(readOnly = true)
    public List<ServerBranchDTO> getBranches(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));
        return branchRepository.findByServerIdOrderByCreatedAtAsc(serverId).stream()
                .map(ServerBranchDTO::from)
                .toList();
    }

    @Transactional
    public ServerBranchDTO createBranch(Long serverId, CreateBranchRequest request, Long userId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        if (branchRepository.existsByServerIdAndName(serverId, request.name())) {
            throw new BadRequestException("分支名称 \"" + request.name() + "\" 已存在");
        }

        Snapshot fromSnapshot = null;
        if (request.fromSnapshotId() != null) {
            fromSnapshot = snapshotRepository.findById(request.fromSnapshotId())
                    .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + request.fromSnapshotId()));
        } else {
            // Use the latest snapshot on this server as the base
            fromSnapshot = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId)
                    .stream().filter(s -> s.getHash() != null).findFirst().orElse(null);
        }

        ServerBranch branch = ServerBranch.builder()
                .server(server)
                .name(request.name())
                .description(request.description())
                .createdFromSnapshot(fromSnapshot)
                .isDefault(false)
                .build();
        branch = branchRepository.save(branch);

        log.info("Created branch {} for server {}", branch.getName(), serverId);
        return ServerBranchDTO.from(branch);
    }

    @Transactional
    public void deleteBranch(Long serverId, Long branchId) {
        ServerBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("分支不存在: " + branchId));

        if (!branch.getServer().getId().equals(serverId)) {
            throw new BadRequestException("分支不属于该服务器");
        }
        if (branch.isDefault()) {
            throw new BadRequestException("不能删除默认分支");
        }

        branchRepository.delete(branch);
        log.info("Deleted branch {} from server {}", branch.getName(), serverId);
    }

    @Transactional
    public ServerBranchDTO switchBranch(Long serverId, Long branchId, Long userId) {
        ServerBranch targetBranch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("分支不存在: " + branchId));

        if (!targetBranch.getServer().getId().equals(serverId)) {
            throw new BadRequestException("分支不属于该服务器");
        }

        // Find the latest snapshot on the target branch
        List<Snapshot> branchSnapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId);
        Snapshot targetSnapshot = branchSnapshots.stream()
                .filter(s -> targetBranch.getId().equals(s.getBranch() != null ? s.getBranch().getId() : null)
                        && s.getHash() != null)
                .findFirst()
                .orElse(targetBranch.getCreatedFromSnapshot());

        if (targetSnapshot == null || targetSnapshot.getHash() == null) {
            throw new BadRequestException("目标分支没有可恢复的快照");
        }

        // Restore the target branch snapshot
        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }

        try {
            SshConnection conn = sshManager.getConnection(targetBranch.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
            boolean success = resticClient.restore(conn, repoUrl, resticPassword,
                    targetSnapshot.getHash(), "/");

            if (!success) {
                throw new BadRequestException("切换分支时恢复快照失败");
            }

            log.info("Switched server {} to branch {} (snapshot {})", serverId, targetBranch.getName(), targetSnapshot.getId());
            return ServerBranchDTO.from(targetBranch);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("切换分支失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public ServerBranchDTO mergeBranches(Long serverId, MergeBranchRequest request, Long userId) {
        ServerBranch source = branchRepository.findById(request.sourceBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("源分支不存在: " + request.sourceBranchId()));
        ServerBranch target = branchRepository.findById(request.targetBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("目标分支不存在: " + request.targetBranchId()));

        if (!source.getServer().getId().equals(serverId) || !target.getServer().getId().equals(serverId)) {
            throw new BadRequestException("分支不属于该服务器");
        }

        // Find latest snapshots for both branches
        List<Snapshot> allSnapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId);

        Snapshot sourceSnapshot = allSnapshots.stream()
                .filter(s -> source.getId().equals(s.getBranch() != null ? s.getBranch().getId() : null)
                        && s.getHash() != null)
                .findFirst().orElse(source.getCreatedFromSnapshot());

        Snapshot targetSnapshot = allSnapshots.stream()
                .filter(s -> target.getId().equals(s.getBranch() != null ? s.getBranch().getId() : null)
                        && s.getHash() != null)
                .findFirst().orElse(target.getCreatedFromSnapshot());

        if (sourceSnapshot == null || sourceSnapshot.getHash() == null) {
            throw new BadRequestException("源分支没有可合并的快照");
        }
        if (targetSnapshot == null || targetSnapshot.getHash() == null) {
            throw new BadRequestException("目标分支没有可合并的快照");
        }

        // Merge: restore source snapshot files onto the server, then create a new snapshot on the target branch
        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }

        try {
            SshConnection conn = sshManager.getConnection(source.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));

            // First restore target branch state
            boolean targetOk = resticClient.restore(conn, repoUrl, resticPassword,
                    targetSnapshot.getHash(), "/");
            if (!targetOk) {
                throw new BadRequestException("恢复目标分支状态失败");
            }

            // Then overlay source branch files
            boolean sourceOk = resticClient.restore(conn, repoUrl, resticPassword,
                    sourceSnapshot.getHash(), "/");
            if (!sourceOk) {
                throw new BadRequestException("应用源分支变更失败");
            }

            log.info("Merged branch {} into branch {} on server {}", source.getName(), target.getName(), serverId);
            return ServerBranchDTO.from(target);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("合并分支失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public ServerBranchDTO renameBranch(Long serverId, Long branchId, String newName) {
        ServerBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("分支不存在: " + branchId));

        if (!branch.getServer().getId().equals(serverId)) {
            throw new BadRequestException("分支不属于该服务器");
        }
        if (branch.isDefault()) {
            throw new BadRequestException("不能重命名默认分支");
        }
        if (branchRepository.existsByServerIdAndName(serverId, newName)) {
            throw new BadRequestException("分支名称 \"" + newName + "\" 已存在");
        }

        branch.setName(newName);
        branch = branchRepository.save(branch);
        return ServerBranchDTO.from(branch);
    }
}
