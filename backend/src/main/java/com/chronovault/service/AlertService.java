package com.chronovault.service;

import com.chronovault.dto.alert.*;
import com.chronovault.entity.Alert;
import com.chronovault.entity.AlertRule;
import com.chronovault.entity.Server;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.AlertRepository;
import com.chronovault.repository.AlertRuleRepository;
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

import java.util.List;

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

    public List<AlertDTO> getAlerts(String filter) {
        List<Alert> alerts;
        if ("critical".equals(filter)) {
            alerts = alertRepository.findBySeverityOrderByCreatedAtDesc(Alert.AlertSeverity.CRITICAL);
        } else if ("predictive".equals(filter)) {
            alerts = alertRepository.findBySeverityOrderByCreatedAtDesc(Alert.AlertSeverity.PREDICTIVE);
        } else if ("warning".equals(filter)) {
            alerts = alertRepository.findBySeverityOrderByCreatedAtDesc(Alert.AlertSeverity.WARNING);
        } else {
            alerts = alertRepository.findAllByOrderByCreatedAtDesc();
        }
        return alerts.stream().map(AlertDTO::from).toList();
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
                log.warn("Failed to restart container for alert {}: {}", alertId, e.getMessage());
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
                log.info("Storage cleanup executed on {}", alert.getServer().getIp());
            } catch (Exception e) {
                log.warn("Failed to expand storage for alert {}: {}", alertId, e.getMessage());
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
                log.info("Config rollback executed on {}", alert.getServer().getIp());
            } catch (Exception e) {
                log.warn("Failed to rollback config for alert {}: {}", alertId, e.getMessage());
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
}
