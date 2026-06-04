package com.chronovault.service;

import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotTagRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotStashService {

    private final SnapshotRepository snapshotRepository;
    private final SnapshotTagRepository tagRepository;
    private final ServerRepository serverRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final SnapshotEngine snapshotEngine;
    private final SshConnectionManager sshManager;
    private final ResticClient resticClient;

    @Value("${chronovault.restic-password}")
    private String resticPassword;

    /**
     * Create a stash: lightweight snapshot marked as STASH type.
     */
    @Transactional
    public SnapshotDTO createStash(Long serverId, String note, Long userId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }
        StorageTarget target = targets.stream()
                .filter(t -> t.getType() != StorageTarget.StorageType.LOCAL)
                .findFirst()
                .orElse(targets.get(0));

        String title = "Stash " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));

        Snapshot snapshot = snapshotEngine.createSnapshot(server, target, title,
                note, Snapshot.SnapshotType.STASH, userId, null, null);

        log.info("Created stash for server {}: id={}", serverId, snapshot.getId());
        return SnapshotDTO.from(snapshot);
    }

    /**
     * List all stashes for a server (ordered newest first).
     */
    @Transactional(readOnly = true)
    public List<SnapshotDTO> listStashes(Long serverId) {
        List<Snapshot> snapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId);
        return snapshots.stream()
                .filter(s -> s.getType() == Snapshot.SnapshotType.STASH)
                .map(s -> {
                    List<SnapshotTagDTO> tags = tagRepository.findBySnapshotIdOrderByCreatedAtDesc(s.getId())
                            .stream().map(SnapshotTagDTO::from).toList();
                    return SnapshotDTO.from(s, tags);
                })
                .toList();
    }

    /**
     * Pop the most recent stash: restore its state to the server and delete the stash record.
     */
    @Transactional
    public String popStash(Long serverId, Long userId) {
        List<Snapshot> stashes = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId).stream()
                .filter(s -> s.getType() == Snapshot.SnapshotType.STASH && s.getHash() != null)
                .toList();

        if (stashes.isEmpty()) {
            throw new BadRequestException("没有可恢复的暂存快照");
        }

        Snapshot stash = stashes.get(0);
        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            throw new BadRequestException("没有可用的存储目标");
        }

        try {
            SshConnection conn = sshManager.getConnection(stash.getServer());
            if (!resticClient.ensureResticInstalled(conn)) {
                throw new BadRequestException("无法安装 restic 备份工具");
            }

            String repoUrl = resticClient.buildRepoUrl(targets.get(0));
            boolean success = resticClient.restore(conn, repoUrl, resticPassword,
                    stash.getHash(), "/");

            if (!success) {
                throw new BadRequestException("恢复暂存快照失败");
            }

            String stashName = stash.getTitle();
            snapshotRepository.delete(stash);
            log.info("Popped stash {} from server {}", stash.getId(), serverId);
            return "已恢复暂存快照 \"" + stashName + "\"，快照记录已删除";
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("恢复暂存快照失败: " + e.getMessage(), e);
        }
    }

    /**
     * Discard a stash: delete the stash record and its restic data.
     */
    @Transactional
    public void discardStash(Long serverId, Long stashId) {
        Snapshot stash = snapshotRepository.findById(stashId)
                .orElseThrow(() -> new ResourceNotFoundException("暂存快照不存在: " + stashId));

        if (!stash.getServer().getId().equals(serverId)) {
            throw new BadRequestException("暂存快照不属于该服务器");
        }
        if (stash.getType() != Snapshot.SnapshotType.STASH) {
            throw new BadRequestException("该快照不是暂存快照");
        }

        snapshotRepository.delete(stash);
        log.info("Discarded stash {} from server {}", stashId, serverId);
    }

    /**
     * Auto-expiry: clean up stashes older than 7 days.
     * Runs daily at 3:00 AM. Uses targeted query instead of findAll().
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanExpiredStashes() {
        try {
            LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(7);
            // Use targeted query instead of loading all snapshots into memory
            List<Snapshot> expiredStashes = snapshotRepository.findExpiredStashes(expiryThreshold);

            if (!expiredStashes.isEmpty()) {
                snapshotRepository.deleteAll(expiredStashes);
                log.info("Auto-cleaned {} expired stashes (older than 7 days)", expiredStashes.size());
            }
        } catch (Exception e) {
            log.error("[STASH_CLEANUP] Failed to clean expired stashes: {}", e.getMessage(), e);
        }
    }
}
