package com.chronovault.ai;

import com.chronovault.cache.CacheService;
import com.chronovault.entity.*;
import com.chronovault.repository.*;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AiClient aiClient;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final ServerRepository serverRepository;
    private final SnapshotRepository snapshotRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final RiskRepository riskRepository;
    private final AlertRepository alertRepository;
    private final SshConnectionManager sshManager;

    private static final String CACHE_RISK_RADAR = "ai:risk_radar";
    private static final String CACHE_STORAGE_PREDICTION = "ai:storage_prediction";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    public Map<String, Double> getRiskRadar() {
        // Check cache
        Map<String, Double> cached = cacheService.get(CACHE_RISK_RADAR,
                new TypeReference<Map<String, Double>>() {});
        if (cached != null) return cached;

        // Collect real metrics
        double dataSecurity = calculateDataSecurityScore();
        double systemStability = calculateSystemStabilityScore();
        double backupCompleteness = calculateBackupScore();
        double networkProtection = calculateNetworkScore();
        double storageHealth = calculateStorageHealthScore();

        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("数据安全", dataSecurity);
        scores.put("系统稳定", systemStability);
        scores.put("备份完整", backupCompleteness);
        scores.put("网络防护", networkProtection);
        scores.put("存储健康", storageHealth);

        // Try AI enhancement
        if (aiClient.isEnabled()) {
            try {
                String prompt = buildRiskRadarPrompt(scores);
                String response = aiClient.chat(
                        "你是一个服务器基础设施风险分析师。根据提供的指标数据，分析并调整风险评分。返回JSON格式的5个维度评分(0-100)。",
                        prompt);
                if (response != null) {
                    Map<String, Double> aiScores = parseScores(response);
                    if (aiScores != null && !aiScores.isEmpty()) {
                        scores = aiScores;
                    }
                }
            } catch (Exception e) {
                log.warn("AI risk radar enhancement failed: {}", e.getMessage());
            }
        }

        cacheService.put(CACHE_RISK_RADAR, scores, CACHE_TTL);
        return scores;
    }

    public List<Map<String, Object>> getStoragePrediction() {
        List<Map<String, Object>> cached = cacheService.get(CACHE_STORAGE_PREDICTION,
                new TypeReference<List<Map<String, Object>>>() {});
        if (cached != null) return cached;

        List<Map<String, Object>> predictions = new ArrayList<>();

        // Get historical storage data
        Long currentUsed = storageTargetRepository.sumUsedBytes();
        if (currentUsed == null) currentUsed = 0L;

        // Calculate growth rate from snapshots
        List<Snapshot> snapshots = snapshotRepository.findAll();
        long totalSnapshotSize = snapshots.stream()
                .mapToLong(s -> s.getSizeBytes() != null ? s.getSizeBytes() : 0)
                .sum();

        // Simple linear projection
        double monthlyGrowth = totalSnapshotSize > 0 ? totalSnapshotSize / Math.max(1, snapshots.size()) * 30.0 : 50L * 1073741824L;
        long projected = currentUsed;

        for (int i = 1; i <= 6; i++) {
            projected += (long) monthlyGrowth;
            Map<String, Object> month = new LinkedHashMap<>();
            month.put("label", "+" + i + "月");
            month.put("bytes", projected);
            predictions.add(month);
        }

        cacheService.put(CACHE_STORAGE_PREDICTION, predictions, CACHE_TTL);
        return predictions;
    }

    public String generateReport() {
        StringBuilder context = new StringBuilder();
        context.append("=== ChronoVault 系统状态报告 ===\n\n");

        // Servers
        List<Server> servers = serverRepository.findAll();
        context.append("服务器: ").append(servers.size()).append(" 台\n");
        long activeServers = servers.stream().filter(s -> s.getStatus() == Server.ServerStatus.RUNNING).count();
        context.append("在线: ").append(activeServers).append(" 台\n\n");

        // Snapshots
        long snapshotCount = snapshotRepository.count();
        context.append("快照总数: ").append(snapshotCount).append("\n\n");

        // Storage
        Long usedBytes = storageTargetRepository.sumUsedBytes();
        Long totalBytes = storageTargetRepository.sumTotalBytes();
        context.append("存储使用: ").append(formatBytes(usedBytes)).append(" / ").append(formatBytes(totalBytes)).append("\n\n");

        // Risks
        long critical = riskRepository.countByLevel(Risk.RiskLevel.CRITICAL);
        long warning = riskRepository.countByLevel(Risk.RiskLevel.WARNING);
        context.append("严重风险: ").append(critical).append("\n");
        context.append("警告风险: ").append(warning).append("\n\n");

        // Alerts
        long alertsToday = alertRepository.countToday();
        context.append("今日告警: ").append(alertsToday).append("\n");

        String prompt = "基于以下 ChronoVault 系统数据，生成一份简洁的分析报告，包含：\n1. 系统健康评估\n2. 主要风险点\n3. 优化建议\n4. 预计影响\n\n数据：\n" + context;

        String report = aiClient.chat(
                "你是一个专业的服务器运维分析师。请用中文生成简洁的分析报告。",
                prompt);

        if (report != null) {
            return report;
        }

        // Fallback to rule-based report
        return generateFallbackReport(servers, snapshotCount, usedBytes, totalBytes, critical, warning);
    }

    private double calculateDataSecurityScore() {
        long critical = riskRepository.countByLevel(Risk.RiskLevel.CRITICAL);
        return Math.max(0, 100 - critical * 15);
    }

    private double calculateSystemStabilityScore() {
        List<Server> servers = serverRepository.findAll();
        if (servers.isEmpty()) return 100;
        long active = servers.stream().filter(s -> s.getStatus() == Server.ServerStatus.RUNNING).count();
        return Math.round((double) active / servers.size() * 100);
    }

    private double calculateBackupScore() {
        long snapshots = snapshotRepository.count();
        if (snapshots == 0) return 50;
        return Math.min(100, 60 + snapshots * 5);
    }

    private double calculateNetworkScore() {
        long alerts = alertRepository.countToday();
        return Math.max(0, 100 - alerts * 10);
    }

    private double calculateStorageHealthScore() {
        Long used = storageTargetRepository.sumUsedBytes();
        Long total = storageTargetRepository.sumTotalBytes();
        if (total == null || total == 0) return 100;
        double usage = (double) used / total * 100;
        return Math.max(0, 100 - usage);
    }

    private String buildRiskRadarPrompt(Map<String, Double> scores) {
        StringBuilder sb = new StringBuilder("当前系统指标:\n");
        scores.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
        sb.append("\n请分析并返回JSON格式的调整后评分。");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> parseScores(String json) {
        try {
            // Try to extract JSON from response
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String jsonStr = json.substring(start, end + 1);
                return objectMapper.readValue(jsonStr, Map.class);
            }
        } catch (Exception e) {
            log.debug("Failed to parse AI scores: {}", e.getMessage());
        }
        return null;
    }

    private String generateFallbackReport(List<Server> servers, long snapshots, Long used, Long total,
                                           long critical, long warning) {
        StringBuilder report = new StringBuilder();
        report.append("## ChronoVault 系统分析报告\n\n");
        report.append("### 1. 系统健康评估\n");
        report.append("- 在线服务器: ").append(servers.size()).append(" 台\n");
        report.append("- 快照总数: ").append(snapshots).append("\n");
        report.append("- 存储使用率: ").append(formatBytes(used)).append(" / ").append(formatBytes(total)).append("\n\n");

        report.append("### 2. 主要风险点\n");
        if (critical > 0) report.append("- 存在 ").append(critical).append(" 个严重风险需要立即处理\n");
        if (warning > 0) report.append("- 存在 ").append(warning).append(" 个警告级别风险\n");
        if (critical == 0 && warning == 0) report.append("- 当前无重大风险\n");

        report.append("\n### 3. 优化建议\n");
        if (snapshots < 5) report.append("- 建议增加快照频率以提高数据安全性\n");
        if (total != null && used != null && total > 0 && (double) used / total > 0.8) {
            report.append("- 存储使用率较高，建议清理旧快照或扩展存储\n");
        }
        report.append("- 建议定期执行风险扫描\n");

        return report.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1073741824L) return String.format("%.1f GB", bytes / 1073741824.0);
        if (bytes >= 1048576L) return String.format("%.1f MB", bytes / 1048576.0);
        return bytes + " B";
    }
}
