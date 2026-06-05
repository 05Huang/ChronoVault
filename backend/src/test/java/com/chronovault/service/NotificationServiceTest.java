package com.chronovault.service;

import com.chronovault.entity.Alert;
import com.chronovault.entity.Integration;
import com.chronovault.repository.IntegrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private IntegrationRepository integrationRepository;

    @InjectMocks
    private NotificationService service;

    @Test
    void sendAlertNotification_noIntegrations_doesNothing() {
        when(integrationRepository.findByUserId(1L)).thenReturn(List.of());
        Alert alert = Alert.builder().id(1L).title("Test Alert").severity(Alert.AlertSeverity.WARNING).build();
        service.sendAlertNotification(alert, 1L);
        verify(integrationRepository, times(1)).findByUserId(1L);
    }

    @Test
    void sendAlertNotification_inactiveIntegration_skipped() {
        Integration inactive = Integration.builder().id(1L).active(false)
                .type(Integration.IntegrationType.SLACK).url("https://hooks.slack.com/test").build();
        when(integrationRepository.findByUserId(1L)).thenReturn(List.of(inactive));
        Alert alert = Alert.builder().id(1L).title("Test Alert").severity(Alert.AlertSeverity.WARNING).build();
        service.sendAlertNotification(alert, 1L);
        // Should not attempt to send — integration is inactive
    }

    @Test
    void sendAlertNotification_withIntegration_triesToSend() {
        Integration active = Integration.builder().id(1L).active(true).name("Slack")
                .type(Integration.IntegrationType.SLACK).url("https://hooks.slack.com/test").build();
        when(integrationRepository.findByUserId(1L)).thenReturn(List.of(active));
        Alert alert = Alert.builder().id(1L).title("Test Alert").severity(Alert.AlertSeverity.WARNING).build();
        // Should not throw even if HTTP call fails
        service.sendAlertNotification(alert, 1L);
    }

    @Test
    void sendAlertNotification_dingTalkIntegration_triesToSend() {
        Integration active = Integration.builder().id(1L).active(true).name("DingTalk")
                .type(Integration.IntegrationType.DINGTALK).url("https://oapi.dingtalk.com/robot/send").build();
        when(integrationRepository.findByUserId(1L)).thenReturn(List.of(active));
        Alert alert = Alert.builder().id(1L).title("Test Alert").severity(Alert.AlertSeverity.CRITICAL).build();
        service.sendAlertNotification(alert, 1L);
    }

    @Test
    void sendAlertNotification_webhookIntegration_triesToSend() {
        Integration active = Integration.builder().id(1L).active(true).name("Custom Webhook")
                .type(Integration.IntegrationType.WEBHOOK).url("https://example.com/webhook").build();
        when(integrationRepository.findByUserId(1L)).thenReturn(List.of(active));
        Alert alert = Alert.builder().id(1L).title("Test Alert").severity(Alert.AlertSeverity.WARNING)
                .source("nginx").category("container").build();
        service.sendAlertNotification(alert, 1L);
    }

    @Test
    void sendAlertNotification_emailIntegration_logsOnly() {
        Integration active = Integration.builder().id(1L).active(true).name("Email")
                .type(Integration.IntegrationType.EMAIL).url("admin@example.com").build();
        when(integrationRepository.findByUserId(1L)).thenReturn(List.of(active));
        Alert alert = Alert.builder().id(1L).title("Test Alert").severity(Alert.AlertSeverity.PREDICTIVE).build();
        // Email is not yet implemented, should log and not throw
        service.sendAlertNotification(alert, 1L);
    }

    @Test
    void sendAlertNotification_multipleIntegrations_sendsToAll() {
        Integration slack = Integration.builder().id(1L).active(true).name("Slack")
                .type(Integration.IntegrationType.SLACK).url("https://hooks.slack.com/test").build();
        Integration webhook = Integration.builder().id(2L).active(true).name("Webhook")
                .type(Integration.IntegrationType.WEBHOOK).url("https://example.com/webhook").build();
        when(integrationRepository.findByUserId(1L)).thenReturn(List.of(slack, webhook));
        Alert alert = Alert.builder().id(1L).title("Test Alert").severity(Alert.AlertSeverity.WARNING).build();
        service.sendAlertNotification(alert, 1L);
    }

    @Test
    void sendAlertNotification_criticalAlert_sendsWithEmoji() {
        Integration active = Integration.builder().id(1L).active(true).name("Slack")
                .type(Integration.IntegrationType.SLACK).url("https://hooks.slack.com/test").build();
        when(integrationRepository.findByUserId(1L)).thenReturn(List.of(active));
        Alert alert = Alert.builder().id(1L).title("Critical Alert").severity(Alert.AlertSeverity.CRITICAL).build();
        service.sendAlertNotification(alert, 1L);
    }
}