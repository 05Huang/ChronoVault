package com.chronovault.service;

import com.chronovault.ai.AiAnalysisService;
import com.chronovault.ai.AiClient;
import com.chronovault.ai.AnomalyDetectionEngine;
import com.chronovault.ai.BackupRecommendationEngine;
import com.chronovault.dto.ai.BackupRecommendationDTO;
import com.chronovault.dto.ai.*;
import com.chronovault.entity.*;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.*;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiInsightRepository aiInsightRepository;
    private final AiRecommendationRepository aiRecommendationRepository;
    private final AiAnalysisService aiAnalysisService;
    private final ServerRepository serverRepository;
    private final ContainerRepository containerRepository;
    private final VolumeRepository volumeRepository;
    private final SnapshotRepository snapshotRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final AiClient aiClient;
    private final SshConnectionManager sshManager;
    private final BackupRecommendationEngine backupRecommendationEngine;
    private final AnomalyDetectionEngine anomalyDetectionEngine;

    // In-memory cache for AI analysis results (TTL: 15 minutes)
    private final ConcurrentHashMap<String, CacheEntry> analysisCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 15 * 60 * 1000;

    private record CacheEntry(ServerAnalysisDTO data, long timestamp) {
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_TTL_MS; }
    }

    @Transactional
    public List<AiInsightDTO> getInsights() {
        // Safety limit: cap at 50 insights to prevent OOM
        List<AiInsight> insights = aiInsightRepository.findTop50ByOrderByCreatedAtDesc();
        if (insights.isEmpty()) {
            seedDefaultInsights();
            insights = aiInsightRepository.findTop50ByOrderByCreatedAtDesc();
        }
        // Regenerate REPORT insights that contain fallback content
        regenerateFallbackReports(insights);
        return insights.stream().map(AiInsightDTO::from).toList();
    }

    private void regenerateFallbackReports(List<AiInsight> insights) {
        for (AiInsight insight : insights) {
            if ("REPORT".equals(insight.getCategory()) && insight.getDescription() != null
                    && insight.getDescription().contains("基础分析（AI 服务未连接")) {
                try {
                    String report = aiAnalysisService.generateReport();
                    insight.setDescription(report);
                    aiInsightRepository.save(insight);
                } catch (Exception e) {
                    log.debug("Failed to regenerate report: {}", e.getMessage());
                }
            }
        }
    }

    @Transactional
    public List<AiRecommendationDTO> getRecommendations() {
        // Safety limit: cap at 50 recommendations to prevent OOM
        List<AiRecommendation> recs = aiRecommendationRepository.findTop50ByOrderByCreatedAtDesc();
        if (recs.isEmpty()) {
            seedDefaultRecommendations();
            recs = aiRecommendationRepository.findTop50ByOrderByCreatedAtDesc();
        }
        return recs.stream().map(AiRecommendationDTO::from).toList();
    }

    private void seedDefaultInsights() {
        // Generate a real report (uses AI if key configured, otherwise rule-based fallback)
        try {
            String report = aiAnalysisService.generateReport();
            aiInsightRepository.save(AiInsight.builder()
                    .title("系统分析报告")
                    .description(report)
                    .icon("analytics")
                    .category("REPORT")
                    .severity("INFO")
                    .build());
        } catch (Exception e) {
            log.warn("Failed to generate initial insight: {}", e.getMessage());
        }

        // Add contextual insights based on real data
        try {
            long serverCount = serverRepository.count();
            long activeCount = serverRepository.countByStatus(Server.ServerStatus.RUNNING);
            aiInsightRepository.save(AiInsight.builder()
                    .title("服务器概览")
                    .description("当前监控 " + serverCount + " 台服务器，其中 " + activeCount + " 台在线。")
                    .icon("dns")
                    .category("SYSTEM")
                    .severity(serverCount == activeCount ? "INFO" : "WARNING")
                    .build());
        } catch (Exception ignored) {}

        try {
            long snapshotCount = snapshotRepository.count();
            long todayCount = snapshotRepository.countToday();
            String desc = snapshotCount == 0
                    ? "当前无快照备份，建议立即创建快照以确保数据安全。"
                    : "共 " + snapshotCount + " 个快照" + (todayCount > 0 ? "，今日新增 " + todayCount + " 个" : "") + "。";
            aiInsightRepository.save(AiInsight.builder()
                    .title("快照状态")
                    .description(desc)
                    .icon("cached")
                    .category("BACKUP")
                    .severity(snapshotCount == 0 ? "WARNING" : "INFO")
                    .build());
        } catch (Exception ignored) {}
    }

    private void seedDefaultRecommendations() {
        List<AiRecommendation> recs = new ArrayList<>();

        // Check snapshots
        long snapshotCount = snapshotRepository.count();
        if (snapshotCount == 0) {
            recs.add(AiRecommendation.builder().title("创建首个快照")
                    .description("当前无任何快照备份，建议立即为关键服务器创建快照以确保数据安全。")
                    .icon("add_circle").impact("HIGH").applied(false).build());
        } else if (snapshotCount < 3) {
            recs.add(AiRecommendation.builder().title("增加快照频率")
                    .description("当前仅有 " + snapshotCount + " 个快照，建议配置定期自动快照策略。")
                    .icon("schedule").impact("HIGH").applied(false).build());
        }

        // Check storage usage
        Long usedBytes = storageTargetRepository.sumUsedBytes();
        Long totalBytes = storageTargetRepository.sumTotalBytes();
        if (usedBytes != null && totalBytes != null && totalBytes > 0) {
            double usage = (double) usedBytes / totalBytes * 100;
            if (usage > 80) {
                recs.add(AiRecommendation.builder().title("存储空间告急")
                        .description(String.format("存储使用率已达 %.1f%%，建议清理过期快照或扩展存储空间。", usage))
                        .icon("warning").impact("HIGH").applied(false).build());
            } else if (usage > 50) {
                recs.add(AiRecommendation.builder().title("关注存储增长")
                        .description(String.format("存储使用率 %.1f%%，建议定期清理过期数据。", usage))
                        .icon("cleaning_services").impact("MEDIUM").applied(false).build());
            }
        }

        // Check server status
        long serverCount = serverRepository.count();
        if (serverCount == 0) {
            recs.add(AiRecommendation.builder().title("添加服务器")
                    .description("尚未添加任何服务器，请先添加服务器开始监控。")
                    .icon("add_box").impact("HIGH").applied(false).build());
        }

        // Check containers
        long containerCount = containerRepository.count();
        if (containerCount > 0) {
            long stopped = containerRepository.findAll().stream()
                    .filter(c -> c.getStatus() != com.chronovault.entity.Container.ContainerStatus.RUNNING)
                    .count();
            if (stopped > 0) {
                recs.add(AiRecommendation.builder().title("容器异常")
                        .description(stopped + " 个容器处于非运行状态，建议检查是否需要重启。")
                        .icon("restart_alt").impact("MEDIUM").applied(false).build());
            }
        }

        // If no specific recommendations, add a general one
        if (recs.isEmpty()) {
            recs.add(AiRecommendation.builder().title("系统运行良好")
                    .description("当前各项指标正常，建议保持定期检查。")
                    .icon("check_circle").impact("LOW").applied(false).build());
        }

        aiRecommendationRepository.saveAll(recs);
    }

    @Transactional
    public void applyRecommendation(Long id) {
        AiRecommendation rec = aiRecommendationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("建议不存在: " + id));
        rec.setApplied(true);
        aiRecommendationRepository.save(rec);
    }

    public RiskRadarDTO getRiskRadar() {
        Map<String, Double> scores = aiAnalysisService.getRiskRadar();
        List<String> keys = List.of("数据安全", "系统稳定", "备份完整", "网络防护", "存储健康");
        List<Map<String, Object>> indicators = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (String key : keys) {
            double val = scores.getOrDefault(key, 0.0);
            indicators.add(Map.of("max", 100, "name", key));
            values.add(val);
        }
        return new RiskRadarDTO(indicators, values);
    }

    public Map<String, Object> getStoragePrediction() {
        return aiAnalysisService.getStoragePrediction();
    }

    @Transactional(readOnly = true)
    public BackupRecommendationDTO getBackupRecommendations() {
        return backupRecommendationEngine.generateRecommendations();
    }

    @Transactional(readOnly = true)
    public AnomalyDetectionDTO detectAnomalies(Long serverId) {
        return anomalyDetectionEngine.detectAnomalies(serverId);
    }

    @Transactional(readOnly = true)
    public List<AnomalyDetectionDTO> detectAllAnomalies() {
        return anomalyDetectionEngine.detectAllAnomalies();
    }

    @Transactional
    public String generateReport() {
        String report = aiAnalysisService.generateReport();

        // Replace old report insights (keep only the latest)
        List<AiInsight> existing = aiInsightRepository.findAll().stream()
                .filter(i -> "REPORT".equals(i.getCategory()))
                .toList();
        aiInsightRepository.deleteAll(existing);

        AiInsight insight = AiInsight.builder()
                .title("AI 分析报告")
                .description(report)
                .category("REPORT")
                .severity("INFO")
                .build();
        aiInsightRepository.save(insight);

        return report;
    }

    public ServerAnalysisDTO analyzeServer(Long serverId) {
        String cacheKey = "server-analysis-" + serverId;
        CacheEntry cached = analysisCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Returning cached AI analysis for server {}", serverId);
            return cached.data();
        }

        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        List<Container> containers = containerRepository.findByServerId(serverId);
        List<Volume> volumes = volumeRepository.findByServerId(serverId);
        List<Snapshot> snapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId);

        long running = containers.stream().filter(c -> c.getStatus() == Container.ContainerStatus.RUNNING).count();
        long stopped = containers.size() - running;
        double avgCpu = containers.stream()
                .filter(c -> c.getCpuPercent() != null)
                .mapToDouble(Container::getCpuPercent).average().orElse(0);
        double avgMem = containers.stream()
                .filter(c -> c.getMemoryPercent() != null)
                .mapToDouble(Container::getMemoryPercent).average().orElse(0);

        // Build context for AI
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== 服务器基本信息 ===\n");
        ctx.append("名称: ").append(server.getName()).append("\n");
        ctx.append("IP: ").append(server.getIp()).append("\n");
        ctx.append("系统: ").append(server.getOs()).append("\n");
        ctx.append("状态: ").append(server.getStatus()).append("\n");
        ctx.append("运行时间: ").append(server.getUptimeSeconds() != null ? server.getUptimeSeconds() + "秒" : "未知").append("\n\n");

        // Fetch live system metrics via SSH
        try {
            SshConnection conn = sshManager.getConnection(server);
            SshConnection.CommandResult result = conn.executeCommand(
                    "echo '===SYS===' && free -m | awk 'NR==2{printf \"内存总量:%sMB 已用:%sMB 使用率:%.1f%%\\n\", $2, $3, $3*100/$2}' && df -h / | awk 'NR==2{printf \"磁盘总量:%s 已用:%s 可用:%s 使用率:%s\\n\", $2, $3, $4, $5}' && cat /proc/loadavg 2>/dev/null | awk '{printf \"负载:%s %s %s\\n\", $1, $2, $3}' && uptime -p 2>/dev/null && echo '===END==='");
            if (result.isSuccess()) {
                ctx.append("=== 系统资源 (实时 SSH) ===\n");
                ctx.append(result.stdout()).append("\n");
            }
        } catch (Exception e) {
            log.debug("Failed to fetch live metrics: {}", e.getMessage());
        }

        ctx.append("=== 容器 ===\n");
        ctx.append("总数: ").append(containers.size()).append(", 运行中: ").append(running).append(", 已停止: ").append(stopped).append("\n");
        ctx.append("平均 CPU: ").append(String.format("%.1f", avgCpu)).append("%, 平均内存: ").append(String.format("%.1f", avgMem)).append("%\n");
        for (Container c : containers) {
            ctx.append("  - ").append(c.getName()).append(": ").append(c.getStatus())
               .append(", CPU=").append(c.getCpuPercent() != null ? String.format("%.1f", c.getCpuPercent()) + "%" : "N/A")
               .append(", MEM=").append(c.getMemoryPercent() != null ? String.format("%.1f", c.getMemoryPercent()) + "%" : "N/A");
            if (c.getNetworks() != null && !c.getNetworks().isBlank()) {
                ctx.append(", 网络=").append(c.getNetworks());
            }
            ctx.append("\n");
        }

        ctx.append("\n=== 存储 ===\n");
        ctx.append("挂载卷: ").append(volumes.size()).append(" 个\n");
        for (Volume v : volumes) {
            ctx.append("  - ").append(v.getName()).append(": ").append(v.getContainerPath()).append("\n");
        }

        ctx.append("\n=== 快照 ===\n");
        ctx.append("总数: ").append(snapshots.size()).append("\n");
        if (!snapshots.isEmpty()) {
            ctx.append("最新: ").append(snapshots.get(0).getCreatedAt())
               .append(" (").append(snapshots.get(0).getStatus()).append(")\n");
        }

        String prompt = "基于以下 ChronoVault 服务器数据，用中文返回 JSON（不要 markdown 代码块），格式：\n" +
                "{\"healthScore\":0-100的整数,\"summary\":\"一句话总评\",\"findings\":[\"发现1\",\"发现2\"],\"recommendations\":[\"建议1\",\"建议2\"]}\n\n" + ctx;

        ServerAnalysisDTO result;
        try {
            String aiResponse = aiClient.chat("你是专业的服务器运维分析师。分析以下服务器数据，给出健康评分、发现的问题和优化建议。只返回合法 JSON，不要任何其他文字。", prompt);
            if (aiResponse != null) {
                result = parseServerAnalysis(aiResponse, containers, running, avgCpu, avgMem);
            } else {
                result = buildFallbackAnalysis(server, containers, running, stopped, avgCpu, avgMem, snapshots);
            }
        } catch (Exception e) {
            log.debug("AI server analysis failed: {}", e.getMessage());
            result = buildFallbackAnalysis(server, containers, running, stopped, avgCpu, avgMem, snapshots);
        }

        analysisCache.put(cacheKey, new CacheEntry(result, System.currentTimeMillis()));
        return result;
    }

    private ServerAnalysisDTO parseServerAnalysis(String aiResponse, List<Container> containers,
            long running, double avgCpu, double avgMem) {
        try {
            // Try to extract JSON from response (AI might wrap it in markdown code blocks)
            String jsonStr = aiResponse;
            // Remove markdown code block wrapper if present
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.replaceAll("```\\s*", "");
            }
            jsonStr = jsonStr.trim();

            int start = jsonStr.indexOf('{');
            int end = jsonStr.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String extracted = jsonStr.substring(start, end + 1);
                Map<String, Object> raw = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(extracted, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                int score = raw.get("healthScore") instanceof Number n ? n.intValue() : calculateHealthScore(running, containers.size(), avgCpu, avgMem);
                String summary = raw.get("summary") instanceof String s ? s : "服务器运行正常";
                List<String> findings = raw.get("findings") instanceof List<?> l ?
                        l.stream().map(Object::toString).toList() : List.of();
                List<String> recs = raw.get("recommendations") instanceof List<?> l ?
                        l.stream().map(Object::toString).toList() : List.of();
                return ServerAnalysisDTO.builder()
                        .healthScore(score).summary(summary)
                        .findings(findings).recommendations(recs).build();
            }
            log.warn("AI response does not contain valid JSON: {}", aiResponse.substring(0, Math.min(200, aiResponse.length())));
        } catch (Exception e) {
            log.warn("Failed to parse AI server analysis: {}", e.getMessage());
        }
        return ServerAnalysisDTO.builder()
                .healthScore(calculateHealthScore(running, containers.size(), avgCpu, avgMem))
                .summary("服务器运行正常").findings(List.of()).recommendations(List.of()).build();
    }

    private ServerAnalysisDTO buildFallbackAnalysis(Server server, List<Container> containers,
            long running, long stopped, double avgCpu, double avgMem, List<Snapshot> snapshots) {
        int score = calculateHealthScore(running, containers.size(), avgCpu, avgMem);
        List<String> findings = new ArrayList<>();
        if (stopped > 0) findings.add(stopped + " 个容器已停止运行");
        if (avgCpu > 80) findings.add("CPU 平均使用率偏高 (" + String.format("%.0f", avgCpu) + "%)");
        if (avgMem > 80) findings.add("内存平均使用率偏高 (" + String.format("%.0f", avgMem) + "%)");
        if (snapshots.isEmpty()) findings.add("暂无快照备份");
        if (findings.isEmpty()) findings.add("各项指标正常");

        List<String> recs = new ArrayList<>();
        if (stopped > 0) recs.add("检查已停止的容器是否需要重启");
        if (avgCpu > 80 || avgMem > 80) recs.add("考虑扩容或优化资源配置");
        if (snapshots.isEmpty()) recs.add("建议创建定期快照策略");
        if (snapshots.size() > 0 && snapshots.size() < 3) recs.add("建议增加快照频率");
        if (recs.isEmpty()) recs.add("当前状态良好，保持监控");

        return ServerAnalysisDTO.builder()
                .healthScore(score)
                .summary(server.getStatus() == Server.ServerStatus.RUNNING ? "服务器运行正常" : "服务器状态异常")
                .findings(findings).recommendations(recs).build();
    }

    private int calculateHealthScore(long running, int total, double avgCpu, double avgMem) {
        if (total == 0) return 100;
        double containerScore = (double) running / total * 40;
        double cpuScore = Math.max(0, (100 - avgCpu) / 100) * 30;
        double memScore = Math.max(0, (100 - avgMem) / 100) * 30;
        return (int) Math.round(containerScore + cpuScore + memScore);
    }
}
