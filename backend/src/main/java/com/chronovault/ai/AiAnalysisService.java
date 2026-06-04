package com.chronovault.ai;

import com.chronovault.cache.CacheService;
import com.chronovault.cache.CacheKeyBuilder;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

    public Map<String, Double> getRiskRadar() {
        // Check cache
        Map<String, Double> cached = cacheService.get(CacheKeyBuilder.aiRiskRadar(),
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

        cacheService.put(CacheKeyBuilder.aiRiskRadar(), scores, CacheKeyBuilder.AI_CACHE_TTL);
        return scores;
    }

    public Map<String, Object> getStoragePrediction() {
        Map<String, Object> cached = cacheService.get(CacheKeyBuilder.aiStoragePrediction(),
                new TypeReference<Map<String, Object>>() {});
        if (cached != null) return cached;

        List<Snapshot> snapshots = snapshotRepository.findAll();

        // Group snapshots by month to build actual historical usage
        Map<YearMonth, Long> monthlyUsage = new TreeMap<>();
        for (Snapshot s : snapshots) {
            if (s.getSizeBytes() == null || s.getCreatedAt() == null) continue;
            YearMonth ym = YearMonth.from(s.getCreatedAt());
            monthlyUsage.merge(ym, s.getSizeBytes(), Long::sum);
        }

        // Build cumulative actual usage
        List<String> months = new ArrayList<>();
        List<Long> actual = new ArrayList<>();
        long cumulative = 0;
        for (Map.Entry<YearMonth, Long> entry : monthlyUsage.entrySet()) {
            cumulative += entry.getValue();
            months.add(entry.getKey().getMonthValue() + "月");
            actual.add(cumulative);
        }

        // Calculate average monthly growth from actual data
        double monthlyGrowth;
        if (actual.size() >= 2) {
            long totalGrowth = actual.get(actual.size() - 1) - actual.get(0);
            monthlyGrowth = (double) totalGrowth / (actual.size() - 1);
        } else if (cumulative > 0) {
            monthlyGrowth = cumulative * 0.1; // 10% monthly growth estimate
        } else {
            Long currentUsed = storageTargetRepository.sumUsedBytes();
            monthlyGrowth = (currentUsed != null && currentUsed > 0) ? currentUsed * 0.05 : 1024L * 1024L * 1024L;
        }

        // Predict next 6 months
        List<Long> predicted = new ArrayList<>();
        long projected = !actual.isEmpty() ? actual.get(actual.size() - 1) : (storageTargetRepository.sumUsedBytes() != null ? storageTargetRepository.sumUsedBytes() : 0L);
        YearMonth lastMonth = !monthlyUsage.isEmpty() ? monthlyUsage.keySet().iterator().next() : YearMonth.now();
        // Get the last key from the TreeMap
        for (YearMonth ym : monthlyUsage.keySet()) lastMonth = ym;

        for (int i = 1; i <= 6; i++) {
            projected += (long) monthlyGrowth;
            predicted.add(projected);
            YearMonth nextMonth = lastMonth.plusMonths(i);
            if (months.size() < 12) { // Cap at 12 months total
                months.add(nextMonth.getMonthValue() + "月");
            }
        }

        // Pad actual list with nulls for prediction months
        List<Long> actualPadded = new ArrayList<>(actual);
        while (actualPadded.size() < months.size()) {
            actualPadded.add(null);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("months", months);
        result.put("actual", actualPadded);
        result.put("predicted", predicted);

        cacheService.put(CacheKeyBuilder.aiStoragePrediction(), result, CacheKeyBuilder.AI_CACHE_TTL);
        return result;
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

    public String analyzeEnvironment(Long serverId, Map<String, Object> scanData) {
        Server server = serverRepository.findById(serverId)
                .orElse(null);
        String serverName = server != null ? server.getName() : "未知服务器";

        StringBuilder prompt = new StringBuilder();
        prompt.append("服务器名称: ").append(serverName).append("\n\n");
        prompt.append("环境扫描数据:\n");
        prompt.append("- 操作系统: ").append(scanData.getOrDefault("os", "未知")).append("\n");
        prompt.append("- 磁盘: ").append(scanData.getOrDefault("disk", "未知")).append("\n");
        prompt.append("- 内存: ").append(scanData.getOrDefault("memory", "未知")).append("\n");
        prompt.append("- 运行时间: ").append(scanData.getOrDefault("uptime", "未知")).append("\n");
        prompt.append("- Docker 已安装: ").append(scanData.getOrDefault("dockerInstalled", false)).append("\n");

        Object containersObj = scanData.get("containers");
        if (containersObj instanceof List<?> containers && !containers.isEmpty()) {
            prompt.append("- 容器数量: ").append(containers.size()).append("\n");
            for (Object c : containers) {
                if (c instanceof Map<?, ?> m) {
                    prompt.append("  - ").append(m.get("name")).append(" (").append(m.get("image"))
                            .append("): ").append(m.get("status")).append("\n");
                }
            }
        }

        Object dbsObj = scanData.get("databases");
        if (dbsObj instanceof List<?> dbs && !dbs.isEmpty()) {
            prompt.append("- 检测到数据库:\n");
            for (Object d : dbs) {
                if (d instanceof Map<?, ?> m) {
                    prompt.append("  - ").append(m.get("type")).append(" (端口 ").append(m.get("port")).append(")\n");
                }
            }
        }

        String analysis = aiClient.chat(
                "你是服务器运维专家。分析以下服务器环境数据，给出：\n1. 环境概况总结\n2. 潜在风险和问题\n3. 优化建议\n4. 安全建议\n\n用中文回答，格式清晰。",
                prompt.toString());

        if (analysis != null) {
            return analysis;
        }
        return generateFallbackEnvironmentAnalysis(scanData);
    }

    private String generateFallbackEnvironmentAnalysis(Map<String, Object> data) {
        StringBuilder report = new StringBuilder();
        report.append("## 服务器环境分析报告\n");
        report.append("> 基础分析（AI 服务未连接，基于规则引擎生成）\n\n");
        report.append("### 1. 环境概况\n");
        report.append("- 操作系统: ").append(data.getOrDefault("os", "未知")).append("\n");
        report.append("- 磁盘使用: ").append(data.getOrDefault("disk", "未知")).append("\n");
        report.append("- 内存使用: ").append(data.getOrDefault("memory", "未知")).append("\n");
        report.append("- 运行时间: ").append(data.getOrDefault("uptime", "未知")).append("\n\n");

        boolean dockerInstalled = Boolean.TRUE.equals(data.get("dockerInstalled"));
        report.append("### 2. Docker 状态\n");
        if (dockerInstalled) {
            Object containersObj = data.get("containers");
            int count = containersObj instanceof List<?> ? ((List<?>) containersObj).size() : 0;
            report.append("- Docker 已安装，检测到 ").append(count).append(" 个容器\n");
        } else {
            report.append("- Docker 未安装\n");
        }

        Object dbsObj = data.get("databases");
        if (dbsObj instanceof List<?> dbs && !dbs.isEmpty()) {
            report.append("\n### 3. 数据库服务\n");
            for (Object d : dbs) {
                if (d instanceof Map<?, ?> m) {
                    report.append("- ").append(m.get("type")).append(" 运行在端口 ").append(m.get("port")).append("\n");
                }
            }
        }

        report.append("\n### 4. 建议\n");
        report.append("- 建议配置定期快照备份\n");
        report.append("- 建议监控磁盘和内存使用率\n");
        if (dockerInstalled) report.append("- 建议定期更新容器镜像以修复安全漏洞\n");

        return report.toString();
    }

    private double calculateDataSecurityScore() {
        long critical = riskRepository.countByLevel(Risk.RiskLevel.CRITICAL);
        return Math.max(0, 100 - critical * 15);
    }

    private double calculateSystemStabilityScore() {
        List<Server> servers = serverRepository.findAll();
        if (servers.isEmpty()) return 100;
        long active = 0;
        for (Server server : servers) {
            if (server.getStatus() == Server.ServerStatus.RUNNING) {
                active++;
            } else {
                // Test actual SSH connectivity as fallback
                try {
                    sshManager.getConnection(server);
                    active++;
                } catch (Exception ignored) {}
            }
        }
        return Math.round((double) active / servers.size() * 100);
    }

    private double calculateBackupScore() {
        long total = snapshotRepository.count();
        if (total == 0) return 0;
        long success = snapshotRepository.countByStatus(Snapshot.SnapshotStatus.STABLE);
        long recent = snapshotRepository.countToday();
        // Score based on: success rate (40pts) + total count (30pts) + recency (30pts)
        double successRate = (double) success / total;
        double countScore = Math.min(1.0, total / 10.0);
        double recencyScore = recent > 0 ? 1.0 : 0.5;
        return Math.round((successRate * 40 + countScore * 30 + recencyScore * 30) * 10.0) / 10.0;
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

    private Map<String, Double> parseScores(String json) {
        try {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String jsonStr = json.substring(start, end + 1);
                Map<String, Object> raw = objectMapper.readValue(jsonStr,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                Map<String, Double> result = new LinkedHashMap<>();
                for (var entry : raw.entrySet()) {
                    if (entry.getValue() instanceof Number n) {
                        result.put(entry.getKey(), n.doubleValue());
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.debug("Failed to parse AI scores: {}", e.getMessage());
        }
        return null;
    }

    private String generateFallbackReport(List<Server> servers, long snapshots, Long used, Long total,
                                           long critical, long warning) {
        StringBuilder report = new StringBuilder();
        report.append("## ChronoVault 系统分析报告\n");
        report.append("> 基础分析（AI 服务未连接，基于规则引擎生成）\n\n");
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
