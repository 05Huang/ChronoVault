package com.chronovault.service;

import com.chronovault.entity.Snapshot;
import com.chronovault.entity.SnapshotRetentionPolicy;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotRetentionPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotRetentionService {

    private final SnapshotRetentionPolicyRepository retentionPolicyRepository;
    private final SnapshotRepository snapshotRepository;

    @Scheduled(cron = "0 0 3 * * *") // 每天凌晨 3 点执行
    @Transactional
    public void executeRetentionCleanup() {
        List<SnapshotRetentionPolicy> policies = retentionPolicyRepository.findByEnabledTrue();
        if (policies.isEmpty()) return;

        log.info("Starting snapshot retention cleanup for {} policies", policies.size());
        int totalDeleted = 0;

        for (SnapshotRetentionPolicy policy : policies) {
            try {
                int deleted = applyRetentionPolicy(policy);
                totalDeleted += deleted;
                policy.setLastRunAt(LocalDateTime.now());
                policy.setDeletedCount(policy.getDeletedCount() + deleted);
                retentionPolicyRepository.save(policy);
                log.info("Retention policy '{}' applied: {} snapshots deleted", policy.getName(), deleted);
            } catch (Exception e) {
                log.error("Failed to apply retention policy '{}': {}", policy.getName(), e.getMessage());
            }
        }

        log.info("Snapshot retention cleanup completed: {} total snapshots deleted", totalDeleted);
    }

    private int applyRetentionPolicy(SnapshotRetentionPolicy policy) {
        Long serverId = policy.getServer().getId();
        List<Snapshot> allSnapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId);
        if (allSnapshots.isEmpty()) return 0;

        List<Snapshot> toDelete = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 保护期：minKeepDays 内的快照不删除
        LocalDateTime protectBefore = now.minusDays(policy.getMinKeepDays());
        List<Snapshot> protectedSnapshots = allSnapshots.stream()
                .filter(s -> s.getCreatedAt().isAfter(protectBefore))
                .toList();
        List<Snapshot> eligibleSnapshots = allSnapshots.stream()
                .filter(s -> !s.getCreatedAt().isAfter(protectBefore))
                .toList();

        // 策略 1：按数量限制 — 保留最新的 maxCount 个（含保护期内的）
        if (policy.getMaxCount() != null && policy.getMaxCount() > 0) {
            int totalKeep = policy.getMaxCount();
            int protectedCount = protectedSnapshots.size();
            int needFromEligible = Math.max(0, totalKeep - protectedCount);

            if (eligibleSnapshots.size() > needFromEligible) {
                // eligibleSnapshots 按时间降序排列，后面的要删
                toDelete.addAll(eligibleSnapshots.subList(needFromEligible, eligibleSnapshots.size()));
            }
        }

        // 策略 2：按天数限制 — 删除超过 maxAgeDays 的快照
        if (policy.getMaxAgeDays() != null && policy.getMaxAgeDays() > 0) {
            LocalDateTime cutoff = now.minusDays(policy.getMaxAgeDays());
            List<Snapshot> expired = eligibleSnapshots.stream()
                    .filter(s -> s.getCreatedAt().isBefore(cutoff))
                    .toList();
            for (Snapshot s : expired) {
                if (!toDelete.contains(s)) {
                    toDelete.add(s);
                }
            }
        }

        if (!toDelete.isEmpty()) {
            log.info("Retention policy '{}': deleting {} snapshots for server {}",
                    policy.getName(), toDelete.size(), serverId);
            snapshotRepository.deleteAll(toDelete);
        }

        return toDelete.size();
    }

    @Transactional
    public SnapshotRetentionPolicy createPolicy(SnapshotRetentionPolicy policy) {
        return retentionPolicyRepository.save(policy);
    }

    @Transactional(readOnly = true)
    public List<SnapshotRetentionPolicy> getPoliciesByServer(Long serverId) {
        return retentionPolicyRepository.findByServerId(serverId);
    }

    @Transactional(readOnly = true)
    public List<SnapshotRetentionPolicy> getAllPolicies() {
        return retentionPolicyRepository.findAll();
    }

    @Transactional
    public void deletePolicy(Long id) {
        retentionPolicyRepository.deleteById(id);
    }

    @Transactional
    public SnapshotRetentionPolicy togglePolicy(Long id) {
        SnapshotRetentionPolicy policy = retentionPolicyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("保留策略不存在: " + id));
        policy.setEnabled(!policy.getEnabled());
        return retentionPolicyRepository.save(policy);
    }
}
