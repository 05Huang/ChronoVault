package com.chronovault.service;

import com.chronovault.dto.dashboard.*;
import com.chronovault.dto.risk.RiskScoreDTO;
import com.chronovault.entity.Alert;
import com.chronovault.entity.Event;
import com.chronovault.entity.Server;
import com.chronovault.repository.*;
import com.chronovault.repository.ContainerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ServerRepository serverRepository;
    private final SnapshotRepository snapshotRepository;
    private final AlertRepository alertRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ContainerRepository containerRepository;
    private final RiskRepository riskRepository;
    private final EventRepository eventRepository;
    private final com.chronovault.cache.CacheService cacheService;

    public DashboardStatsDTO getStats() {
        // Check cache first
        DashboardStatsDTO cached = cacheService.get("dashboard:stats", DashboardStatsDTO.class);
        if (cached != null) return cached;
        long totalServers = serverRepository.count();
        long activeServers = serverRepository.countByStatus(Server.ServerStatus.RUNNING);
        long totalContainers = containerRepository.count();
        long totalSnapshots = snapshotRepository.count();
        long todayBackups = snapshotRepository.countToday();
        long alertsToday = alertRepository.countToday();
        Long usedBytes = storageTargetRepository.sumUsedBytes();
        Long totalBytes = storageTargetRepository.sumTotalBytes();
        long teamMembers = teamMemberRepository.count();

        double uptimePercent = 0;
        if (totalServers > 0) {
            uptimePercent = Math.round((double) activeServers / totalServers * 1000.0) / 10.0;
        }

        DashboardStatsDTO stats = new DashboardStatsDTO(
                (int) totalServers,
                (int) activeServers,
                (int) totalContainers,
                (int) todayBackups,
                (int) totalSnapshots,
                (int) alertsToday,
                uptimePercent + "%",
                formatSize(usedBytes != null ? usedBytes : 0),
                formatSize(totalBytes != null ? totalBytes : 0),
                (int) teamMembers,
                uptimePercent
        );

        cacheService.put("dashboard:stats", stats, java.time.Duration.ofMinutes(5));
        return stats;
    }

    public List<AnomalyDTO> getAnomalies() {
        return alertRepository.findAllByOrderByCreatedAtDesc().stream()
                .limit(5)
                .map(a -> new AnomalyDTO(a.getId(), a.getSeverity().name(), a.getTitle(),
                        a.getSource(), a.getCreatedAt().toString()))
                .toList();
    }

    public List<StorageSummaryDTO> getStorageSummary() {
        return storageTargetRepository.findAll().stream()
                .map(t -> {
                    long used = t.getUsedBytes() != null ? t.getUsedBytes() : 0;
                    long total = t.getTotalBytes() != null ? t.getTotalBytes() : 0;
                    double usage = total > 0 ? (double) used / total * 100 : 0;
                    return new StorageSummaryDTO(t.getType().name(), t.getName(),
                            used, total, Math.round(usage * 10.0) / 10.0);
                })
                .toList();
    }

    public RiskScoreDTO getRiskScore() {
        long critical = riskRepository.countByLevel(com.chronovault.entity.Risk.RiskLevel.CRITICAL);
        long warning = riskRepository.countByLevel(com.chronovault.entity.Risk.RiskLevel.WARNING);
        long anomaly = riskRepository.countByLevel(com.chronovault.entity.Risk.RiskLevel.ANOMALOUS);
        double score = Math.max(0, 100 - (critical * 20 + warning * 10 + anomaly * 5));
        String level;
        String summary;
        if (score >= 80) { level = "低风险"; summary = "系统运行良好，未发现显著风险。"; }
        else if (score >= 60) { level = "中风险"; summary = "发现部分潜在风险，建议关注并处理。"; }
        else if (score >= 40) { level = "高风险"; summary = "系统存在高风险项，建议立即处理。"; }
        else { level = "极高风险"; summary = "系统处于极度危险状态，需要立即干预。"; }
        return new RiskScoreDTO(Math.round(score * 10.0) / 10.0, level, summary,
                (int) critical, (int) warning, (int) anomaly);
    }

    public TopologyDTO getTopology() {
        List<Server> servers = serverRepository.findAll();
        List<TopologyDTO.Node> nodes = servers.stream()
                .map(s -> new TopologyDTO.Node(s.getId().toString(), s.getName(), "server", s.getStatus().name()))
                .collect(java.util.stream.Collectors.toList());

        // Only create edges between servers that share the same user (owner)
        // meaning they belong to the same infrastructure
        List<TopologyDTO.Edge> edges = new ArrayList<>();
        Map<Long, List<TopologyDTO.Node>> byUser = servers.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getUser() != null ? s.getUser().getId() : 0L,
                        Collectors.mapping(
                                s -> new TopologyDTO.Node(s.getId().toString(), s.getName(), "server", s.getStatus().name()),
                                Collectors.toList())));

        for (List<TopologyDTO.Node> group : byUser.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    edges.add(new TopologyDTO.Edge(group.get(i).id(), group.get(j).id()));
                }
            }
        }

        return new TopologyDTO(nodes, edges);
    }

    public List<ActivityTrendDTO> getActivityTrend(String range) {
        int days = "24h".equals(range) ? 1 : "7d".equals(range) ? 7 : 30;
        String pattern = days <= 1 ? "HH:00" : "MM-dd";

        Map<String, int[]> dataMap = new TreeMap<>();

        if (days <= 1) {
            // 24-hour view: group by hour
            for (int i = 23; i >= 0; i--) {
                String key = java.time.LocalTime.now().minusHours(i).format(java.time.format.DateTimeFormatter.ofPattern("HH:00"));
                dataMap.put(key, new int[]{0, 0, 0});
            }
        } else {
            // Multi-day view: group by day
            for (int i = days - 1; i >= 0; i--) {
                String day = LocalDate.now().minusDays(i).format(DateTimeFormatter.ofPattern("MM-dd"));
                dataMap.put(day, new int[]{0, 0, 0});
            }
        }

        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(days);
        List<Event> events = eventRepository.findByCreatedAtAfterOrderByCreatedAtDesc(cutoff);
        for (Event event : events) {
            if (event.getCreatedAt() != null && event.getCreatedAt().isAfter(cutoff)) {
                String key;
                if (days <= 1) {
                    key = event.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:00"));
                } else {
                    key = event.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd"));
                }
                int[] val = dataMap.get(key);
                if (val != null) {
                    if ("snapshot".equals(event.getSource())) val[0]++;
                    else if ("alert".equals(event.getSource())) val[1]++;
                    else val[2]++;
                }
            }
        }

        return dataMap.entrySet().stream()
                .map(e -> new ActivityTrendDTO(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    private String formatSize(long bytes) {
        if (bytes >= 1073741824L) return String.format("%.1f GB", bytes / 1073741824.0);
        if (bytes >= 1048576L) return String.format("%.1f MB", bytes / 1048576.0);
        return bytes + " B";
    }
}
