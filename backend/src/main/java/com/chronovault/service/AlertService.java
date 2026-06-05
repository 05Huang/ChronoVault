package com.chronovault.service;

import com.chronovault.dto.alert.*;
import com.chronovault.entity.Alert;
import com.chronovault.entity.AlertRule;
import com.chronovault.entity.Server;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.AlertRepository;
import com.chronovault.repository.AlertRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.chronovault.repository.IntegrationRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.dto.integration.IntegrationDTO;
import com.chronovault.docker.DockerOperationService;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final IntegrationRepository integrationRepository;
    private final UserService userService;
    private final ServerRepository serverRepository;
    private final DockerOperationService dockerService;
    private final SshConnectionManager sshManager;

    /** Deduplication: track last alert time by composite key (title + source) */
    private static final long DEDUP_WINDOW_MINUTES = 5;
    private final Map<String, LocalDateTime> recentAlerts = new ConcurrentHashMap<>();

    /**
     * Check if a similar alert was sent within the deduplication window.
     */
    public boolean isDuplicateAlert(String title, String source) {
        String key = (title != null ? title : "") + "|" + (source != null ? source : "");
        LocalDateTime lastSent = recentAlerts.get(key);
        if (lastSent != null && lastSent.plusMinutes(DEDUP_WINDOW_MINUTES).isAfter(LocalDateTime.now())) {
            log.debug("[ALERT_DEDUP] Suppressed duplicate alert: {} (last sent {})", title, lastSent);
            return true;
        }
        return false;
    }

    /**
     * Record that an alert was sent for deduplication purposes.
     */
    public void recordAlertSent(String title, String source) {
        String key = (title != null ? title : "") + "|" + (source != null ? source : "");
        recentAlerts.put(key, LocalDateTime.now());
        // Cleanup old entries periodically
        if (recentAlerts.size() > 1000) {
            recentAlerts.entrySet().removeIf(e -> e.getValue().plusMinutes(DEDUP_WINDOW_MINUTES * 2).isBefore(LocalDateTime.now()));
        }
    }

    public List<AlertDTO> getAlerts(String filter) {
        // Safety limit: cap at 100 results to prevent OOM on unbounded queries
        var pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Alert> page;
        if ("critical".equals(filter)) {
            page = alertRepository.findBySeverity(Alert.AlertSeverity.CRITICAL, pageable);
        } else if ("predictive".equals(filter)) {
            page = alertRepository.findBySeverity(Alert.AlertSeverity.PREDICTIVE, pageable);
        } else if ("warning".equals(filter)) {
            page = alertRepository.findBySeverity(Alert.AlertSeverity.WARNING, pageable);
        } else {
            page = alertRepository.findAll(pageable);
        }
        return page.getContent().stream().map(AlertDTO::from).toList();
    }

    public Page<AlertDTO> getAlertsPaged(String filter, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Alert> result;
        if ("critical".equals(filter)) {
            result = alertRepository.findBySeverity(Alert.AlertSeverity.CRITICAL, pageable);
        } else if ("warning".equals(filter)) {
            result = alertRepository.findBySeverity(Alert.AlertSeverity.WARNING, pageable);
        } else if ("predictive".equals(filter)) {
            result = alertRepository.findBySeverity(Alert.AlertSeverity.PREDICTIVE, pageable);
        } else {
            result = alertRepository.findAll(pageable);
        }
        return result.map(AlertDTO::from);
    }

    public AlertStatsDTO getStats() {
        return new AlertStatsDTO(
                (int) alertRepository.count(),
                (int) alertRepository.countBySeverity(Alert.AlertSeverity.CRITICAL),
                (int) alertRepository.countBySeverity(Alert.AlertSeverity.PREDICTIVE),
                (int) alertRepository.countBySeverity(Alert.AlertSeverity.WARNING),
                (int) alertRepository.countByStatus(Alert.AlertStatus.OPEN),
                (int) alertRepository.countByStatus(Alert.AlertStatus.RESOLVED)
        );
    }

    @Transactional
    public void restartContainer(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("告警不存在: " + alertId));

        if (alert.getServer() != null) {
            try {
                // Try to restart containers related to the alert source
                String containerName = alert.getSource();
                if (containerName != null && !containerName.isBlank()) {
                    dockerService.restartContainer(alert.getServer(), containerName);
                }
                // Also scan for exited containers and restart them
                List<com.chronovault.entity.Container> containers = dockerService.listContainers(alert.getServer());
                for (com.chronovault.entity.Container c : containers) {
                    if (c.getStatus() == com.chronovault.entity.Container.ContainerStatus.STOPPED
                            || c.getStatus() == com.chronovault.entity.Container.ContainerStatus.ERROR) {
                        dockerService.restartContainer(alert.getServer(), c.getName());
                    }
                }
            } catch (Exception e) {
                log.warn("[ALERT_ACTION] [alert={}] Failed to restart container: {}", alertId, e.getMessage());
            }
        }

        alert.setStatus(Alert.AlertStatus.RESOLVED);
        alertRepository.save(alert);
    }

    @Transactional
    public void expandStorage(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("告警不存在: " + alertId));

        if (alert.getServer() != null) {
            try {
                SshConnection conn = sshManager.getConnection(alert.getServer());
                // Clean up common disk space consumers
                conn.executeCommand("journalctl --vacuum-time=7d");
                conn.executeCommand("docker system prune -f --volumes 2>/dev/null || true");
                conn.executeCommand("apt-get clean 2>/dev/null || yum clean all 2>/dev/null || true");
                log.info("[ALERT_ACTION] [alert={}] Storage cleanup executed on {}", alertId, alert.getServer().getIp());
            } catch (Exception e) {
                log.warn("[ALERT_ACTION] [alert={}] Failed to expand storage: {}", alertId, e.getMessage());
            }
        }

        alert.setStatus(Alert.AlertStatus.RESOLVED);
        alertRepository.save(alert);
    }

    @Transactional
    public void rollbackConfig(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("告警不存在: " + alertId));

        if (alert.getServer() != null) {
            try {
                SshConnection conn = sshManager.getConnection(alert.getServer());
                // Restart common services to restore default config state
                conn.executeCommand("systemctl restart nginx 2>/dev/null || true");
                conn.executeCommand("systemctl restart docker 2>/dev/null || true");
                log.info("[ALERT_ACTION] [alert={}] Config rollback executed on {}", alertId, alert.getServer().getIp());
            } catch (Exception e) {
                log.warn("[ALERT_ACTION] [alert={}] Failed to rollback config: {}", alertId, e.getMessage());
            }
        }

        alert.setStatus(Alert.AlertStatus.RESOLVED);
        alertRepository.save(alert);
    }

    @Transactional
    public void dismiss(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("告警不存在: " + alertId));
        alert.setStatus(Alert.AlertStatus.DISMISSED);
        alertRepository.save(alert);
    }

    public List<AlertRuleDTO> getRules(String email) {
        User user = userService.getByEmail(email);
        return alertRuleRepository.findByUserId(user.getId()).stream()
                .map(AlertRuleDTO::from)
                .toList();
    }

    @Transactional
    public AlertRuleDTO createRule(String email, CreateAlertRuleRequest request) {
        User user = userService.getByEmail(email);
        AlertRule rule = AlertRule.builder()
                .user(user)
                .name(request.name())
                .metric(request.metric())
                .threshold(request.threshold() != null ? request.threshold().doubleValue() : 0.0)
                .durationMinutes(request.durationMinutes() != null ? request.durationMinutes() : 5)
                .severity(request.severity() != null ? Alert.AlertSeverity.valueOf(request.severity()) : Alert.AlertSeverity.WARNING)
                .enabled(request.enabled() != null ? request.enabled() : true)
                .build();
        alertRuleRepository.save(rule);
        return AlertRuleDTO.from(rule);
    }

    // Integration methods
    public List<IntegrationDTO> getIntegrations(String email) {
        User user = userService.getByEmail(email);
        return integrationRepository.findByUserId(user.getId()).stream()
                .map(IntegrationDTO::from)
                .toList();
    }

    @Transactional
    public IntegrationDTO createIntegration(String email, String type, String name, String url) {
        User user = userService.getByEmail(email);
        com.chronovault.entity.Integration integration = com.chronovault.entity.Integration.builder()
                .user(user)
                .type(com.chronovault.entity.Integration.IntegrationType.valueOf(type))
                .name(name)
                .url(url)
                .active(true)
                .build();
        integrationRepository.save(integration);
        return IntegrationDTO.from(integration);
    }

    @Transactional
    public IntegrationDTO updateIntegration(Long id, Boolean active) {
        com.chronovault.entity.Integration integration = integrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("集成不存在: " + id));
        if (active != null) integration.setActive(active);
        integrationRepository.save(integration);
        return IntegrationDTO.from(integration);
    }

    @Transactional
    public void deleteRule(Long id) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("告警规则不存在: " + id));
        alertRuleRepository.delete(rule);
    }

    @Transactional
    public void deleteIntegration(Long id) {
        com.chronovault.entity.Integration integration = integrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("集成不存在: " + id));
        integrationRepository.delete(integration);
    }

    /**
     * Send alert notification to all active integrations for the user.
     */
    public void notifyAlert(String email, Alert alert) {
        User user = userService.getByEmail(email);
        List<com.chronovault.entity.Integration> integrations = integrationRepository.findByUserId(user.getId());
        for (com.chronovault.entity.Integration integration : integrations) {
            if (!Boolean.TRUE.equals(integration.getActive())) continue;
            try {
                sendToChannel(integration, alert);
            } catch (Exception e) {
                log.error("[ALERT_NOTIFY] [alert={}] Failed to send notification via {}: {}", alert.getId(), integration.getType(), e.getMessage());
            }
        }
    }

    /**
     * Escalate a critical alert: force notification to ALL active integrations regardless of event filter.
     * Used for high-risk alerts like SSH disconnect, disk full, etc.
     */
    public void escalateCriticalAlert(Alert alert) {
        if (alert.getSeverity() != Alert.AlertSeverity.CRITICAL) return;

        log.info("[ALERT_ESCALATE] [alert={}] Escalating critical alert: {}", alert.getId(), alert.getTitle());

        // Find the server owner and send to all their active integrations
        if (alert.getServer() != null && alert.getServer().getUser() != null) {
            Long userId = alert.getServer().getUser().getId();
            List<com.chronovault.entity.Integration> integrations = integrationRepository.findByUserId(userId);
            for (com.chronovault.entity.Integration integration : integrations) {
                if (!Boolean.TRUE.equals(integration.getActive())) continue;
                try {
                    sendToChannel(integration, alert);
                    log.info("[ALERT_ESCALATE] [alert={}] Sent via {}", alert.getId(), integration.getType());
                } catch (Exception e) {
                    log.error("[ALERT_ESCALATE] [alert={}] Failed to send via {}: {}", alert.getId(), integration.getType(), e.getMessage());
                }
            }
        } else {
            log.warn("[ALERT_ESCALATE] [alert={}] No server owner found for escalation", alert.getId());
        }
    }

    private void sendToChannel(com.chronovault.entity.Integration integration, Alert alert) {
        String message = String.format("[%s] %s: %s",
                alert.getSeverity(), alert.getTitle(), alert.getDescription());
        switch (integration.getType()) {
            case SLACK -> sendSlackWebhook(integration.getUrl(), message);
            case DINGTALK -> sendDingTalkWebhook(integration.getUrl(), message);
            case WEBHOOK -> sendGenericWebhook(integration.getUrl(), alert);
            case EMAIL -> sendEmail(integration.getUrl(), alert);
            default -> log.warn("[ALERT_NOTIFY] Unsupported integration type: {}", integration.getType());
        }
    }

    private void sendSlackWebhook(String webhookUrl, String message) {
        try {
            String payload = String.format("{\"text\":\"%s\"}", escapeJson(message));
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("[ALERT_NOTIFY] Slack webhook failed: status={}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("[ALERT_NOTIFY] Slack webhook error: {}", e.getMessage());
        }
    }

    private void sendDingTalkWebhook(String webhookUrl, String message) {
        try {
            String payload = String.format("{\"msgtype\":\"text\",\"text\":{\"content\":\"%s\"}}", escapeJson(message));
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("[ALERT_NOTIFY] DingTalk webhook error: {}", e.getMessage());
        }
    }

    private void sendGenericWebhook(String webhookUrl, Alert alert) {
        try {
            String payload = String.format("{\"title\":\"%s\",\"severity\":\"%s\",\"description\":\"%s\",\"source\":\"%s\"}",
                    escapeJson(alert.getTitle()), alert.getSeverity(),
                    escapeJson(alert.getDescription()), alert.getSource());
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("[ALERT_NOTIFY] Webhook error: {}", e.getMessage());
        }
    }

    private void sendEmail(String emailAddress, Alert alert) {
        // Email sending requires SMTP configuration — log for now
        log.info("[ALERT_NOTIFY] [alert={}] Email notification to {}: [{}] {}", alert.getId(), emailAddress, alert.getSeverity(), alert.getTitle());
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
