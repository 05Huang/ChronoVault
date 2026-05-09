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

    public DashboardStatsDTO getStats() {
        long totalServers = serverRepository.count();
        long activeServers = serverRepository.countByStatus(Server.ServerStatus.RUNNING);
        long totalContainers = containerRepository.count();
        long totalSnapshots = snapshotRepository.count();
        long alertsToday = alertRepository.countToday();
        Long usedBytes = storageTargetRepository.sumUsedBytes();
        Long totalBytes = storageTargetRepository.sumTotalBytes();
        long teamMembers = teamMemberRepository.count();

        double uptimePercent = 0;
        if (totalServers > 0) {
            uptimePercent = Math.round((double) activeServers / totalServers * 1000.0) / 10.0;
        }

        return new DashboardStatsDTO(
                (int) totalServers,
                (int) activeServers,
                (int) totalContainers,
                (int) totalSnapshots,
                (int) totalSnapshots,
                (int) alertsToday,
                uptimePercent + "%",
                formatSize(usedBytes != null ? usedBytes : 0),
                formatSize(totalBytes != null ? totalBytes : 0),
                (int) teamMembers,
                uptimePercent
        );
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
        else { level = "高风险"; summary = "系统存在高风险项，建议立即处理。"; }
        return new RiskScoreDTO(Math.round(score * 10.0) / 10.0, level, summary,
                (int) critical, (int) warning, (int) anomaly);
    }

    public TopologyDTO getTopology() {
        List<TopologyDTO.Node> nodes = serverRepository.findAll().stream()
                .map(s -> new TopologyDTO.Node(s.getId().toString(), s.getName(), "server", s.getStatus().name()))
                .collect(java.util.stream.Collectors.toList());
        List<TopologyDTO.Edge> edges = new ArrayList<>();
        for (int i = 1; i < nodes.size(); i++) {
            edges.add(new TopologyDTO.Edge(nodes.get(0).id(), nodes.get(i).id()));
        }
        return new TopologyDTO(nodes, edges);
    }

    public List<ActivityTrendDTO> getActivityTrend() {
        // Aggregate real event data by day
        Map<String, int[]> dailyData = new TreeMap<>();

        for (int i = 6; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).format(DateTimeFormatter.ofPattern("MM-dd"));
            dailyData.put(day, new int[]{0, 0, 0}); // snapshots, alerts, events
        }

        List<Event> events = eventRepository.findAll();
        for (Event event : events) {
            if (event.getCreatedAt() != null) {
                String day = event.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd"));
                int[] data = dailyData.get(day);
                if (data != null) {
                    if ("snapshot".equals(event.getSource())) data[0]++;
                    else if ("alert".equals(event.getSource())) data[1]++;
                    else data[2]++;
                }
            }
        }

        return dailyData.entrySet().stream()
                .map(e -> new ActivityTrendDTO(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    private String formatSize(long bytes) {
        if (bytes >= 1073741824L) return String.format("%.1f GB", bytes / 1073741824.0);
        if (bytes >= 1048576L) return String.format("%.1f MB", bytes / 1048576.0);
        return bytes + " B";
    }
}
