package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.SnapshotRetentionPolicy;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotRetentionPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Snapshot Compliance Service: checks each server's snapshots against its RetentionPolicy,
 * generates compliance reports, and auto-cleans non-compliant snapshots.
 *
 * Report includes:
 * - Total snapshots vs maxCount limit
 * - Oldest snapshot age vs maxAgeDays limit
 * - Protected snapshots within minKeepDays
 * - Non-compliant snapshots that should be cleaned
 * - Overall compliance status (COMPLIANT / NON_COMPLIANT / NO_POLICY)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotComplianceService {

    private final SnapshotRetentionPolicyRepository retentionPolicyRepository;
    private final SnapshotRepository snapshotRepository;
    private final ServerRepository serverRepository;
    private final SnapshotRetentionService retentionService;

    /**
     * Compliance report for a single server.
     */
    public record ComplianceReport(
            long serverId,
            String serverName,
            String policyName,
            String status,            // COMPLIANT / NON_COMPLIANT / NO_POLICY
            int totalSnapshots,
            Integer maxCountLimit,    // null if no count limit
            Integer maxAgeDaysLimit,  // null if no age limit
            int minKeepDays,
            int protectedSnapshots,   // snapshots within minKeepDays
            int nonCompliantCount,    // snapshots that violate the policy
            int daysSinceOldest,      // age of oldest snapshot in days
            String oldestSnapshotDate,
            String newestSnapshotDate,
            List<ComplianceViolation> violations,
            String summary
    ) {}

    /**
     * A single compliance violation.
     */
    public record ComplianceViolation(
            String type,       // EXCEEDS_COUNT / EXCEEDS_AGE / NO_SNAPSHOTS
            String description,
            long snapshotId,
            String snapshotTitle,
            String snapshotDate
    ) {}

    /**
     * Generate compliance report for a single server.
     */
    @Transactional(readOnly = true)
    public ComplianceReport checkServerCompliance(long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new com.chronovault.exception.ResourceNotFoundException("服务器不存在: " + serverId));

        List<SnapshotRetentionPolicy> policies = retentionPolicyRepository.findByServerId(serverId);
        SnapshotRetentionPolicy policy = policies.stream()
                .filter(SnapshotRetentionPolicy::getEnabled)
                .findFirst()
                .orElse(null);

        List<Snapshot> snapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId);

        if (policy == null) {
            return new ComplianceReport(
                    serverId, server.getName(), "无策略",
                    "NO_POLICY", snapshots.size(),
                    null, null, 0, 0, 0,
                    getDaysSinceOldest(snapshots),
                    getOldestDate(snapshots), getNewestDate(snapshots),
                    List.of(),
                    "该服务器未配置保留策略，建议创建策略以管理快照生命周期"
            );
        }

        // Analyze compliance
        List<ComplianceViolation> violations = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime protectBefore = now.minusDays(policy.getMinKeepDays());

        // Count protected snapshots
        int protectedCount = (int) snapshots.stream()
                .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().isAfter(protectBefore))
                .count();

        // Check count limit
        if (policy.getMaxCount() != null && policy.getMaxCount() > 0 && snapshots.size() > policy.getMaxCount()) {
            int excess = snapshots.size() - policy.getMaxCount();
            violations.add(new ComplianceViolation(
                    "EXCEEDS_COUNT",
                    String.format("快照数量 (%d) 超过策略上限 (%d)，超出 %d 个",
                            snapshots.size(), policy.getMaxCount(), excess),
                    0, "", ""
            ));
        }

        // Check age limit
        int nonCompliantAge = 0;
        if (policy.getMaxAgeDays() != null && policy.getMaxAgeDays() > 0) {
            LocalDateTime cutoff = now.minusDays(policy.getMaxAgeDays());
            long expiredCount = snapshots.stream()
                    .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().isBefore(cutoff)
                            && s.getCreatedAt().isBefore(protectBefore))
                    .count();
            if (expiredCount > 0) {
                nonCompliantAge = (int) expiredCount;
                violations.add(new ComplianceViolation(
                        "EXCEEDS_AGE",
                        String.format("%d 个快照超过策略最大保留天数 (%d 天)", expiredCount, policy.getMaxAgeDays()),
                        0, "", ""
                ));
            }
        }

        // Check no snapshots
        if (snapshots.isEmpty()) {
            violations.add(new ComplianceViolation(
                    "NO_SNAPSHOTS",
                    "该服务器没有任何快照，建议立即创建快照",
                    0, "", ""
            ));
        }

        // Determine status
        String status = violations.isEmpty() ? "COMPLIANT" : "NON_COMPLIANT";

        // Build summary
        String summary;
        if (violations.isEmpty()) {
            summary = String.format("合规：共 %d 个快照，策略 '%s' 的所有条件均满足",
                    snapshots.size(), policy.getName());
        } else {
            summary = String.format("不合规：共 %d 个快照，策略 '%s' 检测到 %d 个违规项",
                    snapshots.size(), policy.getName(), violations.size());
        }

        return new ComplianceReport(
                serverId, server.getName(), policy.getName(),
                status, snapshots.size(),
                policy.getMaxCount(), policy.getMaxAgeDays(), policy.getMinKeepDays(),
                protectedCount, nonCompliantAge,
                getDaysSinceOldest(snapshots),
                getOldestDate(snapshots), getNewestDate(snapshots),
                violations, summary
        );
    }

    /**
     * Generate compliance report for all servers.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generateComplianceReport() {
        List<Server> servers = serverRepository.findAll();
        List<ComplianceReport> reports = new ArrayList<>();

        int compliant = 0;
        int nonCompliant = 0;
        int noPolicy = 0;

        for (Server server : servers) {
            try {
                ComplianceReport report = checkServerCompliance(server.getId());
                reports.add(report);
                switch (report.status()) {
                    case "COMPLIANT" -> compliant++;
                    case "NON_COMPLIANT" -> nonCompliant++;
                    default -> noPolicy++;
                }
            } catch (Exception e) {
                log.warn("Failed to check compliance for server {}: {}", server.getId(), e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.put("totalServers", servers.size());
        result.put("compliant", compliant);
        result.put("nonCompliant", nonCompliant);
        result.put("noPolicy", noPolicy);
        result.put("overallStatus", nonCompliant == 0 ? "COMPLIANT" : "NON_COMPLIANT");
        result.put("reports", reports);

        log.info("[COMPLIANCE] Report generated: {}/{} compliant, {} non-compliant, {} no policy",
                compliant, servers.size(), nonCompliant, noPolicy);

        return result;
    }

    /**
     * Auto-clean non-compliant snapshots for all servers with enabled policies.
     * Returns total number of snapshots cleaned.
     */
    @Scheduled(cron = "0 0 4 * * *") // 每天凌晨 4 点执行合规清理
    @Transactional
    public int autoCleanNonCompliant() {
        List<SnapshotRetentionPolicy> policies = retentionPolicyRepository.findByEnabledTrue();
        int totalCleaned = 0;

        log.info("[COMPLIANCE] Starting auto-clean for {} enabled policies", policies.size());

        for (SnapshotRetentionPolicy policy : policies) {
            try {
                List<Snapshot> toDelete = getNonCompliantSnapshots(policy);
                if (!toDelete.isEmpty()) {
                    snapshotRepository.deleteAll(toDelete);
                    totalCleaned += toDelete.size();
                    policy.setLastRunAt(LocalDateTime.now());
                    policy.setDeletedCount(policy.getDeletedCount() + toDelete.size());
                    retentionPolicyRepository.save(policy);
                    log.info("[COMPLIANCE] Cleaned {} non-compliant snapshots for policy '{}'",
                            toDelete.size(), policy.getName());
                }
            } catch (Exception e) {
                log.error("[COMPLIANCE] Failed to clean for policy '{}': {}", policy.getName(), e.getMessage());
            }
        }

        log.info("[COMPLIANCE] Auto-clean completed: {} total snapshots cleaned", totalCleaned);
        return totalCleaned;
    }

    /**
     * Get non-compliant snapshots for a policy (those that should be deleted).
     */
    private List<Snapshot> getNonCompliantSnapshots(SnapshotRetentionPolicy policy) {
        Long serverId = policy.getServer().getId();
        List<Snapshot> snapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId);
        if (snapshots.isEmpty()) return List.of();

        List<Snapshot> toDelete = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime protectBefore = now.minusDays(policy.getMinKeepDays());

        // Only consider snapshots outside the protection period
        List<Snapshot> eligible = snapshots.stream()
                .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().isBefore(protectBefore))
                .toList();

        // Count limit
        if (policy.getMaxCount() != null && policy.getMaxCount() > 0) {
            int protectedCount = (int) snapshots.stream()
                    .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().isAfter(protectBefore))
                    .count();
            int maxFromEligible = Math.max(0, policy.getMaxCount() - protectedCount);
            if (eligible.size() > maxFromEligible) {
                toDelete.addAll(eligible.subList(maxFromEligible, eligible.size()));
            }
        }

        // Age limit
        if (policy.getMaxAgeDays() != null && policy.getMaxAgeDays() > 0) {
            LocalDateTime cutoff = now.minusDays(policy.getMaxAgeDays());
            eligible.stream()
                    .filter(s -> s.getCreatedAt().isBefore(cutoff))
                    .forEach(s -> {
                        if (!toDelete.contains(s)) toDelete.add(s);
                    });
        }

        return toDelete;
    }

    private int getDaysSinceOldest(List<Snapshot> snapshots) {
        return snapshots.stream()
                .filter(s -> s.getCreatedAt() != null)
                .min(Comparator.comparing(Snapshot::getCreatedAt))
                .map(s -> (int) Duration.between(s.getCreatedAt(), LocalDateTime.now()).toDays())
                .orElse(0);
    }

    private String getOldestDate(List<Snapshot> snapshots) {
        return snapshots.stream()
                .filter(s -> s.getCreatedAt() != null)
                .min(Comparator.comparing(Snapshot::getCreatedAt))
                .map(s -> s.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .orElse("N/A");
    }

    private String getNewestDate(List<Snapshot> snapshots) {
        return snapshots.stream()
                .filter(s -> s.getCreatedAt() != null)
                .max(Comparator.comparing(Snapshot::getCreatedAt))
                .map(s -> s.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .orElse("N/A");
    }
}
