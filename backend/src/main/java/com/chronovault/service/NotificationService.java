package com.chronovault.service;

import com.chronovault.entity.Alert;
import com.chronovault.entity.Integration;
import com.chronovault.repository.IntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final IntegrationRepository integrationRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public void sendAlertNotification(Alert alert, Long userId) {
        List<Integration> integrations = integrationRepository.findByUserId(userId);

        for (Integration integration : integrations) {
            if (!Boolean.TRUE.equals(integration.getActive())) continue;

            try {
                switch (integration.getType()) {
                    case SLACK -> sendSlackNotification(integration.getUrl(), alert);
                    case DINGTALK -> sendDingTalkNotification(integration.getUrl(), alert);
                    case FEISHU -> sendFeishuNotification(integration.getUrl(), alert);
                    case WECHAT -> sendWechatNotification(integration.getUrl(), alert);
                    case WEBHOOK -> sendWebhookNotification(integration.getUrl(), alert);
                    case EMAIL -> log.info("Email notification for alert {} (not yet implemented)", alert.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to send notification to {} ({}) for alert {}: {}",
                        integration.getType(), integration.getName(), alert.getId(), e.getMessage());
            }
        }
    }

    private void sendSlackNotification(String webhookUrl, Alert alert) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        String severityEmoji = switch (alert.getSeverity()) {
            case CRITICAL -> "🔴";
            case WARNING -> "🟡";
            case PREDICTIVE -> "🔵";
        };

        String payload = String.format("""
                {
                    "text": "%s ChronoVault Alert",
                    "blocks": [
                        {
                            "type": "section",
                            "text": {
                                "type": "mrkdwn",
                                "text": "%s *%s*\\n%s\\n*Server:* %s\\n*Severity:* %s"
                            }
                        }
                    ]
                }""",
                severityEmoji,
                severityEmoji,
                alert.getTitle(),
                alert.getDescription() != null ? alert.getDescription() : "",
                alert.getServer() != null ? alert.getServer().getName() : "Unknown",
                alert.getSeverity().name()
        );

        restTemplate.postForEntity(webhookUrl, payload, String.class);
        log.info("Slack notification sent for alert: {}", alert.getTitle());
    }

    private void sendDingTalkNotification(String webhookUrl, Alert alert) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        String severityEmoji = switch (alert.getSeverity()) {
            case CRITICAL -> "🔴";
            case WARNING -> "🟡";
            case PREDICTIVE -> "🔵";
        };

        String payload = String.format("""
                {
                    "msgtype": "markdown",
                    "markdown": {
                        "title": "ChronoVault Alert",
                        "text": "## %s ChronoVault 告警\\n\\n**%s**\\n\\n%s\\n\\n**服务器:** %s\\n**级别:** %s"
                    }
                }""",
                severityEmoji,
                alert.getTitle(),
                alert.getDescription() != null ? alert.getDescription() : "",
                alert.getServer() != null ? alert.getServer().getName() : "Unknown",
                alert.getSeverity().name()
        );

        restTemplate.postForEntity(webhookUrl, payload, String.class);
        log.info("DingTalk notification sent for alert: {}", alert.getTitle());
    }

    private void sendWebhookNotification(String webhookUrl, Alert alert) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        String payload = String.format("""
                {
                    "alert_id": %d,
                    "title": "%s",
                    "description": "%s",
                    "severity": "%s",
                    "server": "%s",
                    "source": "%s",
                    "category": "%s"
                }""",
                alert.getId(),
                escapeJson(alert.getTitle()),
                escapeJson(alert.getDescription() != null ? alert.getDescription() : ""),
                alert.getSeverity().name(),
                alert.getServer() != null ? alert.getServer().getName() : "Unknown",
                alert.getSource() != null ? alert.getSource() : "",
                alert.getCategory() != null ? alert.getCategory() : ""
        );

        restTemplate.postForEntity(webhookUrl, payload, String.class);
        log.info("Webhook notification sent for alert: {}", alert.getTitle());
    }

    private void sendFeishuNotification(String webhookUrl, Alert alert) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        String severityEmoji = switch (alert.getSeverity()) {
            case CRITICAL -> "🔴";
            case WARNING -> "🟡";
            case PREDICTIVE -> "🔵";
        };

        String payload = String.format("""
                {
                    "msg_type": "interactive",
                    "card": {
                        "header": {
                            "title": {
                                "tag": "plain_text",
                                "content": "%s ChronoVault 告警"
                            },
                            "template": "red"
                        },
                        "elements": [
                            {
                                "tag": "div",
                                "text": {
                                    "tag": "lark_md",
                                    "content": "**%s**\\n%s\\n\\n**服务器:** %s\\n**级别:** %s"
                                }
                            }
                        ]
                    }
                }""",
                severityEmoji,
                alert.getTitle(),
                alert.getDescription() != null ? alert.getDescription() : "",
                alert.getServer() != null ? alert.getServer().getName() : "Unknown",
                alert.getSeverity().name()
        );

        restTemplate.postForEntity(webhookUrl, payload, String.class);
        log.info("[NOTIFY] Feishu notification sent for alert: {}", alert.getTitle());
    }

    private void sendWechatNotification(String webhookUrl, Alert alert) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        String payload = String.format("""
                {
                    "msgtype": "markdown",
                    "markdown": {
                        "content": "## ChronoVault 告警\\n\\n**%s**\\n\\n%s\\n\\n**服务器:** %s\\n**级别:** %s"
                    }
                }""",
                alert.getTitle(),
                alert.getDescription() != null ? alert.getDescription() : "",
                alert.getServer() != null ? alert.getServer().getName() : "Unknown",
                alert.getSeverity().name()
        );

        restTemplate.postForEntity(webhookUrl, payload, String.class);
        log.info("[NOTIFY] WeChat notification sent for alert: {}", alert.getTitle());
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
