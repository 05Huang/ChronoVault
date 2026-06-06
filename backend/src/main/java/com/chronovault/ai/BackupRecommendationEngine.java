package com.chronovault.ai;

import com.chronovault.dto.ai.BackupRecommendationDTO;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.ScheduledBackup;
import com.chronovault.entity.StorageTarget;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.ScheduledBackupRepository;
import com.chronovault.repository.StorageTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Recommendation Engine: analyzes historical snapshot patterns to recommend
 * backup strategy (frequency, retention policy, path selection).
 *
 * Uses rule-based analysis with optional AI enhancement via MiMo API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupRecommendationEngine {

    private final SnapshotRepository snapshotRepository;
    private final ServerRepository serverRepository;
    private final ScheduledBackupRepository scheduledBackupRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final AiClient aiClient;

    /**
     * Generate backup strategy recommendations for all servers.
     * Combines rule-based analysis with optional AI enhancement.
     */
    public BackupRecommendationDTO generateRecommendations() {
        log.info("[AI_BACKUP_REC] Generating backup strategy recommendations");

        List<Server> servers = serverRepository.findAll();
        List<Snapshot> recentSnapshots = snapshotRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, 200)).getContent();
        Long usedBytes = storageTargetRepository.sumUsedBytes();
        Long totalBytes = storageTargetRepository.sumTotalBytes();
        List<ScheduledBackup> schedules = scheduledBackupRepository.findAll();

        // Rule-based analysis
        BackupRecommendationDTO.FrequencyRecommendation freqRec = analyzeFrequency(servers, recentSnapshots, schedules);
        BackupRecommendationDTO.RetentionRecommendation retentionRec = analyzeRetention(recentSnapshots, usedBytes, totalBytes);
        BackupRecommendationDTO.PathRecommendation pathRec = analyzePaths(recentSnapshots);

        List<BackupRecommendationDTO.ServerBackupSummary> serverSummaries = analyzePerServer(servers, recentSnapshots);

        // Try AI enhancement
        String aiSummary = null;
        try {
            aiSummary = generateAiSummary(freqRec, retentionRec, pathRec, serverSummaries, servers.size(), recentSnapshots.size());
        } catch (Exception e) {
            log.debug("[AI_BACKUP_REC] AI summary generation failed: {}", e.getMessage());
        }

        BackupRecommendationDTO dto = new BackupRecommendationDTO(
                freqRec, retentionRec, pathRec, serverSummaries, aiSummary
        );
        log.info("[AI_BACKUP_REC] Generated recommendations: frequency={}, retention={}, paths={}, servers={}",
                freqRec.suggestedFrequency(), retentionRec.suggestedRetainDays(), pathRec.priorityPaths().size(), serverSummaries.size());
        return dto;
    }

    private BackupRecommendationDTO.FrequencyRecommendation analyzeFrequency(
            List<Server> servers, List<Snapshot> recentSnapshots, List<ScheduledBackup> schedules) {

        if (recentSnapshots.isEmpty()) {
            return new BackupRecommendationDTO.FrequencyRecommendation(
                    "未配置", "当前无快照记录，建议配置每日自动快照", "每日 (0 2 * * *)", "HIGH"
            );
        }

        // Calculate actual snapshot frequency from historical data
        LocalDateTime oldest = recentSnapshots.get(recentSnapshots.size() - 1).getCreatedAt();
        LocalDateTime newest = recentSnapshots.get(0).getCreatedAt();
        long daysBetween = Math.max(1, Duration.between(oldest, newest).toDays());
        double snapshotsPerDay = (double) recentSnapshots.size() / daysBetween;

        // Count servers with auto-snapshot enabled
        long autoEnabled = servers.stream().filter(Server::isAutoSnapshotEnabled).count();
        long hasSchedule = schedules.stream().filter(ScheduledBackup::getEnabled).count();

        // Determine change velocity from change_summary_json presence
        long withChanges = recentSnapshots.stream()
                .filter(s -> s.getChangeSummaryJson() != null && !s.getChangeSummaryJson().isBlank())
                .count();
        double changeRate = (double) withChanges / Math.max(1, recentSnapshots.size());

        // Decision logic (aligned with Backrest's default patterns)
        String suggested;
        String reason;
        String cronSuggestion;
        String priority;

        if (snapshotsPerDay >= 4) {
            suggested = "每6小时";
            reason = String.format("历史快照频率较高 (%.1f 次/天)，系统变更频繁，建议维持高频快照", snapshotsPerDay);
            cronSuggestion = "0 */6 * * *";
            priority = "INFO";
        } else if (snapshotsPerDay >= 1.5) {
            suggested = "每12小时";
            reason = String.format("变更适中 (%.1f 次/天)，建议每12小时快照一次以平衡安全与存储", snapshotsPerDay);
            cronSuggestion = "0 */12 * * *";
            priority = "INFO";
        } else if (snapshotsPerDay >= 0.5) {
            suggested = "每日";
            reason = String.format("变更频率较低 (%.1f 次/天)，每日快照即可满足需求", snapshotsPerDay);
            cronSuggestion = "0 2 * * *";
            priority = "INFO";
        } else {
            suggested = "每日";
            reason = "快照频率不足，建议至少每日执行一次快照以确保数据安全";
            cronSuggestion = "0 2 * * *";
            priority = "HIGH";
        }

        // Warn if auto-snapshot not enabled on any server
        if (autoEnabled == 0 && !servers.isEmpty()) {
            reason += "。注意：所有服务器均未启用自动快照";
        }
        if (hasSchedule == 0 && !schedules.isEmpty()) {
            // no-op, schedules exist but none enabled
        }

        return new BackupRecommendationDTO.FrequencyRecommendation(suggested, reason, cronSuggestion, priority);
    }

    private BackupRecommendationDTO.RetentionRecommendation analyzeRetention(
            List<Snapshot> recentSnapshots, Long usedBytes, Long totalBytes) {

        if (recentSnapshots.isEmpty() || usedBytes == null || totalBytes == null || totalBytes == 0) {
            return new BackupRecommendationDTO.RetentionRecommendation(
                    30, "当前无足够数据计算最优保留策略，默认保留 30 天", "30%", "INFO"
            );
        }

        double storageUsage = (double) usedBytes / totalBytes * 100;
        int snapshotCount = recentSnapshots.size();

        // Calculate average snapshot size
        OptionalDouble avgSize = recentSnapshots.stream()
                .filter(s -> s.getSizeBytes() != null)
                .mapToLong(Snapshot::getSizeBytes)
                .average();
        long avgSnapshotBytes = (long) avgSize.orElse(0);

        // Projected growth per day
        LocalDateTime oldest = recentSnapshots.get(recentSnapshots.size() - 1).getCreatedAt();
        long days = Math.max(1, Duration.between(oldest, LocalDateTime.now()).toDays());
        long totalSnapshotBytes = recentSnapshots.stream()
                .filter(s -> s.getSizeBytes() != null)
                .mapToLong(Snapshot::getSizeBytes)
                .sum();
        long dailyGrowth = totalSnapshotBytes / days;

        // Recommendation logic
        int suggestedDays;
        String reason;
        String freeSpaceTarget;
        String priority;

        if (storageUsage > 85) {
            // Critical: storage nearly full — aggressive retention
            suggestedDays = 7;
            reason = String.format("存储使用率已达 %.1f%%，建议仅保留 7 天快照以释放空间", storageUsage);
            freeSpaceTarget = "50%";
            priority = "HIGH";
        } else if (storageUsage > 60) {
            // Warning: storage moderately used
            suggestedDays = 14;
            reason = String.format("存储使用率 %.1f%%，建议保留 14 天快照并定期清理", storageUsage);
            freeSpaceTarget = "40%";
            priority = "MEDIUM";
        } else if (storageUsage > 30) {
            // Normal
            suggestedDays = 30;
            reason = String.format("存储使用率 %.1f%%，空间充裕，保留 30 天快照可满足回溯需求", storageUsage);
            freeSpaceTarget = "30%";
            priority = "INFO";
        } else {
            // Plenty of space
            suggestedDays = 60;
            reason = String.format("存储使用率仅 %.1f%%，可保留更长时间的快照用于长期回溯", storageUsage);
            freeSpaceTarget = "20%";
            priority = "INFO";
        }

        // Add size-based adjustment: if snapshots are very large, suggest shorter retention
        if (avgSnapshotBytes > 1024L * 1024L * 1024L && suggestedDays > 14) {
            suggestedDays = Math.max(7, suggestedDays - 7);
            reason += String.format("。快照平均大小 %.1f GB，建议缩短保留周期", avgSnapshotBytes / (1024.0 * 1024.0 * 1024.0));
        }

        return new BackupRecommendationDTO.RetentionRecommendation(
                suggestedDays, reason, freeSpaceTarget, priority
        );
    }

    private BackupRecommendationDTO.PathRecommendation analyzePaths(List<Snapshot> recentSnapshots) {
        // Analyze state.json from recent snapshots to identify frequently changing paths
        Map<String, Integer> configChangeFrequency = new LinkedHashMap<>();
        Set<String> alwaysChanging = new LinkedHashSet<>();

        for (Snapshot snap : recentSnapshots.stream().limit(30).toList()) {
            String stateJson = snap.getStateJson();
            if (stateJson == null || stateJson.isBlank()) continue;

            try {
                // Extract config file paths from state.json
                // state.json format: { "configs": [{"path": "/etc/...", "sha256": "..."}] }
                if (stateJson.contains("\"configs\"")) {
                    // Parse config paths from the JSON
                    List<String> configPaths = extractConfigPaths(stateJson);
                    for (String path : configPaths) {
                        configChangeFrequency.merge(path, 1, Integer::sum);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse state.json for path analysis: {}", e.getMessage());
            }
        }

        // Identify paths that change frequently (in >30% of snapshots)
        int threshold = Math.max(1, (int) (recentSnapshots.size() * 0.3));
        for (Map.Entry<String, Integer> entry : configChangeFrequency.entrySet()) {
            if (entry.getValue() >= threshold) {
                alwaysChanging.add(entry.getKey());
            }
        }

        // Standard priority paths (common across most servers)
        List<String> priorityPaths = List.of(
                "/etc",
                "/var/log",
                "/opt",
                "/home"
        );

        // Paths to exclude (volatile, not useful for backup)
        List<String> excludePaths = List.of(
                "/tmp",
                "/var/tmp",
                "/var/cache",
                "/proc",
                "/sys",
                "/dev"
        );

        // Generate per-server priority path suggestions based on detected workloads
        List<BackupRecommendationDTO.ServerPathSuggestion> perServerSuggestions = new ArrayList<>();
        for (Snapshot snap : recentSnapshots.stream().limit(5).toList()) {
            String stateJson = snap.getStateJson();
            if (stateJson == null) continue;

            Long serverId = snap.getServer() != null ? snap.getServer().getId() : null;
            if (serverId == null) continue;

            List<String> detected = detectWorkloadPaths(stateJson);
            if (!detected.isEmpty()) {
                perServerSuggestions.add(new BackupRecommendationDTO.ServerPathSuggestion(
                        serverId, detected, "基于当前状态检测"
                ));
            }
        }

        return new BackupRecommendationDTO.PathRecommendation(
                priorityPaths, excludePaths, perServerSuggestions
        );
    }

    private List<BackupRecommendationDTO.ServerBackupSummary> analyzePerServer(
            List<Server> servers, List<Snapshot> recentSnapshots) {

        Map<Long, List<Snapshot>> byServer = recentSnapshots.stream()
                .filter(s -> s.getServer() != null)
                .collect(Collectors.groupingBy(s -> s.getServer().getId(), LinkedHashMap::new, Collectors.toList()));

        List<BackupRecommendationDTO.ServerBackupSummary> summaries = new ArrayList<>();
        for (Server server : servers) {
            List<Snapshot> snaps = byServer.getOrDefault(server.getId(), List.of());
            long withState = snaps.stream()
                    .filter(s -> s.getStateJson() != null && !s.getStateJson().isBlank())
                    .count();

            String status;
            String suggestion;

            if (snaps.isEmpty()) {
                status = "NO_BACKUP";
                suggestion = "该服务器无任何快照记录，建议立即创建首个快照";
            } else if (snaps.size() < 3) {
                status = "INSUFFICIENT";
                suggestion = String.format("仅 %d 个快照，建议增加快照频率以确保可回溯性", snaps.size());
            } else if (withState == 0) {
                status = "NO_STATE";
                suggestion = "快照缺少 state.json 数据，无法进行状态对比分析，建议启用 Agent 状态采集";
            } else {
                status = "ADEQUATE";
                suggestion = "快照数量充足，保持当前策略";
            }

            summaries.add(new BackupRecommendationDTO.ServerBackupSummary(
                    server.getId(), server.getName(), snaps.size(), withState, status, suggestion
            ));
        }
        return summaries;
    }

    private String generateAiSummary(
            BackupRecommendationDTO.FrequencyRecommendation freq,
            BackupRecommendationDTO.RetentionRecommendation retention,
            BackupRecommendationDTO.PathRecommendation paths,
            List<BackupRecommendationDTO.ServerBackupSummary> servers,
            int totalServers, int totalSnapshots) {

        if (!aiClient.isEnabled()) return null;

        StringBuilder prompt = new StringBuilder();
        prompt.append("基于以下 ChronoVault 备份策略分析数据，生成一段简洁的中文总结（2-3句话）：\n\n");
        prompt.append("服务器总数: ").append(totalServers).append("\n");
        prompt.append("快照总数: ").append(totalSnapshots).append("\n");
        prompt.append("推荐频率: ").append(freq.suggestedFrequency()).append(" - ").append(freq.reason()).append("\n");
        prompt.append("推荐保留: ").append(retention.suggestedRetainDays()).append(" 天 - ").append(retention.reason()).append("\n");
        prompt.append("优先备份路径: ").append(paths.priorityPaths()).append("\n");
        prompt.append("排除路径: ").append(paths.excludePaths()).append("\n");
        long noBackup = servers.stream().filter(s -> "NO_BACKUP".equals(s.status())).count();
        long insufficient = servers.stream().filter(s -> "INSUFFICIENT".equals(s.status())).count();
        prompt.append("无备份服务器: ").append(noBackup).append(" 台\n");
        prompt.append("快照不足服务器: ").append(insufficient).append(" 台\n");

        return aiClient.chat(
                "你是专业的服务器备份策略顾问。根据数据给出简洁总结，不要使用 markdown 格式。",
                prompt.toString()
        );
    }

    // --- Helper methods for state.json parsing ---

    private List<String> extractConfigPaths(String stateJson) {
        // Lightweight JSON parsing without external dependencies
        List<String> paths = new ArrayList<>();
        int idx = stateJson.indexOf("\"configs\"");
        if (idx < 0) return paths;

        // Find the array after "configs":
        int arrStart = stateJson.indexOf('[', idx);
        if (arrStart < 0) return paths;

        // Extract "path" values from the config objects
        int searchFrom = arrStart;
        while (true) {
            int pathIdx = stateJson.indexOf("\"path\"", searchFrom);
            if (pathIdx < 0 || pathIdx > arrStart + 50000) break; // safety limit

            int colonIdx = stateJson.indexOf(':', pathIdx);
            int quoteStart = stateJson.indexOf('"', colonIdx + 1);
            int quoteEnd = stateJson.indexOf('"', quoteStart + 1);

            if (quoteStart > 0 && quoteEnd > quoteStart) {
                String path = stateJson.substring(quoteStart + 1, quoteEnd);
                paths.add(path);
            }
            searchFrom = quoteEnd + 1;
        }
        return paths;
    }

    private List<String> detectWorkloadPaths(String stateJson) {
        List<String> paths = new ArrayList<>();

        // Detect Docker workload
        if (stateJson.contains("\"docker\"") && stateJson.contains("\"containers\"")) {
            paths.add("/var/lib/docker");
        }

        // Detect common services from packages section
        if (stateJson.contains("\"nginx\"")) paths.add("/etc/nginx");
        if (stateJson.contains("\"mysql\"") || stateJson.contains("\"mariadb\"")) {
            paths.add("/etc/mysql");
            paths.add("/var/lib/mysql");
        }
        if (stateJson.contains("\"postgresql\"")) {
            paths.add("/etc/postgresql");
            paths.add("/var/lib/postgresql");
        }
        if (stateJson.contains("\"redis\"")) paths.add("/etc/redis");
        if (stateJson.contains("\"node\"") || stateJson.contains("\"npm\"")) paths.add("/opt");
        if (stateJson.contains("\"docker-compose\"")) paths.add("/opt");

        return paths;
    }
}
