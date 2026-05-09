package com.chronovault.service;

import com.chronovault.dto.risk.RiskDTO;
import com.chronovault.dto.risk.RiskNodeDTO;
import com.chronovault.dto.risk.RiskScoreDTO;
import com.chronovault.dto.risk.RiskTrendDTO;
import com.chronovault.entity.Risk;
import com.chronovault.entity.Server;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.RiskRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.task.AsyncTaskManager;
import com.chronovault.task.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskRepository riskRepository;
    private final ServerRepository serverRepository;
    private final SshConnectionManager sshManager;
    private final AsyncTaskManager taskManager;

    public RiskScoreDTO getScore() {
        long critical = riskRepository.countByLevel(Risk.RiskLevel.CRITICAL);
        long warning = riskRepository.countByLevel(Risk.RiskLevel.WARNING);
        long anomaly = riskRepository.countByLevel(Risk.RiskLevel.ANOMALOUS);

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

    public List<RiskTrendDTO> getTrend() {
        // Aggregate risk counts by day from the database
        List<Risk> allRisks = riskRepository.findAll();
        Map<String, Integer> dailyCounts = new TreeMap<>();

        // Initialize last 30 days
        for (int i = 29; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).format(DateTimeFormatter.ofPattern("MM-dd"));
            dailyCounts.put(day, 0);
        }

        // Count risks by discovered date
        for (Risk risk : allRisks) {
            if (risk.getDiscoveredAt() != null) {
                String day = risk.getDiscoveredAt().format(DateTimeFormatter.ofPattern("MM-dd"));
                dailyCounts.merge(day, 1, Integer::sum);
            }
        }

        return dailyCounts.entrySet().stream()
                .map(e -> new RiskTrendDTO(e.getKey(),
                        Math.max(0, 100 - e.getValue() * 10.0)))
                .toList();
    }

    public List<RiskNodeDTO> getNodes() {
        List<RiskNodeDTO> nodes = new ArrayList<>();
        List<Server> servers = serverRepository.findAll();

        for (Server server : servers) {
            double healthScore = 90.0;
            String status = "HEALTHY";

            try {
                SshConnection conn = sshManager.getConnection(server);
                SshConnection.CommandResult result = conn.executeCommand(
                        "df -h / | tail -1 | awk '{print $5}' | tr -d '%'");
                if (result.isSuccess() && !result.stdout().isBlank()) {
                    int diskUsage = Integer.parseInt(result.stdout().trim());
                    healthScore = Math.max(0, 100 - diskUsage);
                    if (diskUsage > 90) status = "CRITICAL";
                    else if (diskUsage > 75) status = "WARNING";
                }
            } catch (Exception e) {
                healthScore = 0;
                status = "OFFLINE";
            }

            nodes.add(new RiskNodeDTO(server.getName(), Math.round(healthScore * 10.0) / 10.0, status));
        }

        if (nodes.isEmpty()) {
            return List.of(
                    new RiskNodeDTO("无在线服务器", 0.0, "OFFLINE")
            );
        }
        return nodes;
    }

    public List<RiskDTO> getRisks() {
        return riskRepository.findAllByOrderByDiscoveredAtDesc().stream()
                .map(RiskDTO::from)
                .toList();
    }

    @Transactional
    public void mitigate(Long id) {
        Risk risk = riskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("风险不存在: " + id));
        risk.setStatus(Risk.RiskStatus.MITIGATED);
        riskRepository.save(risk);
    }

    @Transactional
    public List<RiskDTO> scan() {
        List<Server> servers = serverRepository.findAll();

        for (Server server : servers) {
            try {
                SshConnection conn = sshManager.getConnection(server);

                // Check disk usage
                SshConnection.CommandResult diskResult = conn.executeCommand(
                        "df -h / | tail -1 | awk '{print $5}' | tr -d '%'");
                if (diskResult.isSuccess() && !diskResult.stdout().isBlank()) {
                    int diskUsage = Integer.parseInt(diskResult.stdout().trim());
                    if (diskUsage > 85) {
                        createRisk(Risk.RiskLevel.WARNING, "HIGH_DISK_USAGE",
                                server.getName() + " 磁盘使用率 " + diskUsage + "%",
                                server.getName());
                    }
                }

                // Check memory
                SshConnection.CommandResult memResult = conn.executeCommand(
                        "free | grep Mem | awk '{printf \"%.0f\", $3/$2 * 100}'");
                if (memResult.isSuccess() && !memResult.stdout().isBlank()) {
                    int memUsage = Integer.parseInt(memResult.stdout().trim());
                    if (memUsage > 90) {
                        createRisk(Risk.RiskLevel.CRITICAL, "HIGH_MEMORY_USAGE",
                                server.getName() + " 内存使用率 " + memUsage + "%",
                                server.getName());
                    }
                }

                // Check Docker containers
                SshConnection.CommandResult dockerResult = conn.executeCommand(
                        "docker ps -a --filter 'status=exited' --format '{{.Names}}' | head -5");
                if (dockerResult.isSuccess() && !dockerResult.stdout().isBlank()) {
                    String[] stoppedContainers = dockerResult.stdout().trim().split("\n");
                    for (String container : stoppedContainers) {
                        if (!container.isBlank()) {
                            createRisk(Risk.RiskLevel.ANOMALOUS, "CONTAINER_STOPPED",
                                    server.getName() + " 容器已停止: " + container,
                                    server.getName());
                        }
                    }
                }

            } catch (Exception e) {
                log.warn("Scan failed for {}: {}", server.getIp(), e.getMessage());
                createRisk(Risk.RiskLevel.CRITICAL, "SERVER_UNREACHABLE",
                        "无法连接到服务器: " + server.getName(),
                        server.getName());
            }
        }

        return getRisks();
    }

    private void createRisk(Risk.RiskLevel level, String type, String description, String source) {
        Risk risk = Risk.builder()
                .level(level)
                .title(type)
                .description(description)
                .source(source)
                .status(Risk.RiskStatus.OPEN)
                .build();
        riskRepository.save(risk);
    }
}
