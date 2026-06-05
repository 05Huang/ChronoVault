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
import com.chronovault.docker.DockerOperationService;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock private AlertRepository alertRepository;
    @Mock private AlertRuleRepository alertRuleRepository;
    @Mock private IntegrationRepository integrationRepository;
    @Mock private UserService userService;
    @Mock private ServerRepository serverRepository;
    @Mock private DockerOperationService dockerService;
    @Mock private SshConnectionManager sshManager;

    @InjectMocks
    private AlertService alertService;

    private Alert testAlert;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Test User").email("test@example.com").build();
        testAlert = Alert.builder().id(1L).title("Test Alert").description("Test description")
                .severity(Alert.AlertSeverity.CRITICAL).status(Alert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void getAlerts_noFilter_returnsAll() {
        // Service now uses paginated query with safety limit
        var page = new org.springframework.data.domain.PageImpl<>(List.of(testAlert));
        when(alertRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        var result = alertService.getAlerts("all");
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getAlerts_criticalFilter_returnsCriticalAlerts() {
        // Service now uses paginated query with safety limit
        var page = new org.springframework.data.domain.PageImpl<>(List.of(testAlert));
        when(alertRepository.findBySeverity(eq(Alert.AlertSeverity.CRITICAL), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        var result = alertService.getAlerts("critical");
        assertEquals(1, result.size());
    }

    @Test
    void getStats_returnsStats() {
        when(alertRepository.count()).thenReturn(10L);
        when(alertRepository.countBySeverity(Alert.AlertSeverity.CRITICAL)).thenReturn(3L);
        when(alertRepository.countBySeverity(Alert.AlertSeverity.PREDICTIVE)).thenReturn(2L);
        when(alertRepository.countBySeverity(Alert.AlertSeverity.WARNING)).thenReturn(5L);
        when(alertRepository.countByStatus(Alert.AlertStatus.OPEN)).thenReturn(7L);
        when(alertRepository.countByStatus(Alert.AlertStatus.RESOLVED)).thenReturn(3L);

        AlertStatsDTO stats = alertService.getStats();
        assertNotNull(stats);
        assertEquals(10, stats.total());
    }

    @Test
    void dismiss_existingAlert_setsDismissed() {
        when(alertRepository.findById(1L)).thenReturn(Optional.of(testAlert));
        alertService.dismiss(1L);
        assertEquals(Alert.AlertStatus.DISMISSED, testAlert.getStatus());
        verify(alertRepository).save(testAlert);
    }

    @Test
    void dismiss_nonExistingAlert_throwsException() {
        when(alertRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> alertService.dismiss(999L));
    }

    @Test
    void restartContainer_existingAlert_setsResolved() {
        when(alertRepository.findById(1L)).thenReturn(Optional.of(testAlert));
        alertService.restartContainer(1L);
        assertEquals(Alert.AlertStatus.RESOLVED, testAlert.getStatus());
        verify(alertRepository).save(testAlert);
    }

    @Test
    void createRule_validRequest_createsRule() {
        when(userService.getByEmail("test@example.com")).thenReturn(testUser);
        CreateAlertRuleRequest request = new CreateAlertRuleRequest("CPU High", "cpu_usage", 80, 5, "CRITICAL", true);
        when(alertRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlertRuleDTO result = alertService.createRule("test@example.com", request);
        assertNotNull(result);
        assertEquals("CPU High", result.name());
    }

    @Test
    void deleteRule_existingRule_deletes() {
        AlertRule rule = AlertRule.builder().id(1L).name("Test Rule").build();
        when(alertRuleRepository.findById(1L)).thenReturn(Optional.of(rule));
        alertService.deleteRule(1L);
        verify(alertRuleRepository).delete(rule);
    }

    @Test
    void deleteRule_nonExistingRule_throwsException() {
        when(alertRuleRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> alertService.deleteRule(999L));
    }

    @Test
    void getRules_returnsUserRules() {
        when(userService.getByEmail("test@example.com")).thenReturn(testUser);
        AlertRule rule = AlertRule.builder().id(1L).name("CPU High").metric("cpu_usage")
                .threshold(80.0).severity(Alert.AlertSeverity.CRITICAL).enabled(true).user(testUser).build();
        when(alertRuleRepository.findByUserId(1L)).thenReturn(List.of(rule));

        var result = alertService.getRules("test@example.com");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CPU High", result.get(0).name());
        assertEquals(80.0, result.get(0).threshold());
    }

    @Test
    void createRule_withCustomThresholdAndDuration() {
        when(userService.getByEmail("test@example.com")).thenReturn(testUser);
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(
                "Disk High", "disk_usage", 90, 10, "WARNING", true);
        when(alertRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlertRuleDTO result = alertService.createRule("test@example.com", request);

        assertNotNull(result);
        assertEquals("Disk High", result.name());
        assertEquals("disk_usage", result.metric());
        assertEquals(90.0, result.threshold());
        assertEquals(10, result.durationMinutes());
        assertEquals("WARNING", result.severity());
        assertTrue(result.enabled());
    }

    @Test
    void createRule_withNullDefaults() {
        when(userService.getByEmail("test@example.com")).thenReturn(testUser);
        CreateAlertRuleRequest request = new CreateAlertRuleRequest("Simple", "cpu_usage", null, null, null, null);
        when(alertRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlertRuleDTO result = alertService.createRule("test@example.com", request);

        assertNotNull(result);
        assertEquals(0.0, result.threshold());
        assertEquals(5, result.durationMinutes());
        assertEquals("WARNING", result.severity());
        assertTrue(result.enabled());
    }

    @Test
    void expandStorage_existingAlert_setsResolved() {
        when(alertRepository.findById(1L)).thenReturn(Optional.of(testAlert));
        alertService.expandStorage(1L);
        assertEquals(Alert.AlertStatus.RESOLVED, testAlert.getStatus());
        verify(alertRepository).save(testAlert);
    }

    @Test
    void rollbackConfig_existingAlert_setsResolved() {
        when(alertRepository.findById(1L)).thenReturn(Optional.of(testAlert));
        alertService.rollbackConfig(1L);
        assertEquals(Alert.AlertStatus.RESOLVED, testAlert.getStatus());
        verify(alertRepository).save(testAlert);
    }

    @Test
    void getIntegrations_returnsUserIntegrations() {
        when(userService.getByEmail("test@example.com")).thenReturn(testUser);
        com.chronovault.entity.Integration integration = com.chronovault.entity.Integration.builder()
                .id(1L).type(com.chronovault.entity.Integration.IntegrationType.SLACK)
                .name("Slack").url("https://hooks.slack.com/test").active(true).user(testUser).build();
        when(integrationRepository.findByUserId(1L)).thenReturn(List.of(integration));

        var result = alertService.getIntegrations("test@example.com");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("SLACK", result.get(0).type());
    }

    @Test
    void createIntegration_validRequest_creates() {
        when(userService.getByEmail("test@example.com")).thenReturn(testUser);
        when(integrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = alertService.createIntegration("test@example.com", "SLACK", "My Slack", "https://hooks.slack.com/test");

        assertNotNull(result);
        assertEquals("SLACK", result.type());
        assertEquals("My Slack", result.name());
    }

    @Test
    void updateIntegration_setsActive() {
        com.chronovault.entity.Integration integration = com.chronovault.entity.Integration.builder()
                .id(1L).type(com.chronovault.entity.Integration.IntegrationType.SLACK)
                .name("Test").url("https://test.com").active(true).build();
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = alertService.updateIntegration(1L, false);

        assertFalse(result.active());
    }

    @Test
    void deleteIntegration_existingIntegration_deletes() {
        com.chronovault.entity.Integration integration = com.chronovault.entity.Integration.builder().id(1L).build();
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(integration));
        alertService.deleteIntegration(1L);
        verify(integrationRepository).delete(integration);
    }

    // =====================================================================
    // Alert Deduplication Tests
    // =====================================================================

    @Test
    void isDuplicateAlert_firstTime_returnsFalse() {
        assertFalse(alertService.isDuplicateAlert("Test Alert", "snapshot-diff"));
    }

    @Test
    void isDuplicateAlert_afterRecord_returnsTrue() {
        alertService.recordAlertSent("Test Alert", "snapshot-diff");
        assertTrue(alertService.isDuplicateAlert("Test Alert", "snapshot-diff"));
    }

    @Test
    void isDuplicateAlert_differentTitle_returnsFalse() {
        alertService.recordAlertSent("Alert A", "snapshot-diff");
        assertFalse(alertService.isDuplicateAlert("Alert B", "snapshot-diff"));
    }

    @Test
    void isDuplicateAlert_differentSource_returnsFalse() {
        alertService.recordAlertSent("Test Alert", "source-a");
        assertFalse(alertService.isDuplicateAlert("Test Alert", "source-b"));
    }

    @Test
    void isDuplicateAlert_nullTitleAndSource_handled() {
        alertService.recordAlertSent(null, null);
        assertTrue(alertService.isDuplicateAlert(null, null));
    }

    // =====================================================================
    // Alert Escalation Tests
    // =====================================================================

    @Test
    void escalateCriticalAlert_sendsToAllIntegrations() {
        Server server = Server.builder().id(1L).name("Test Server").user(testUser).build();
        Alert criticalAlert = Alert.builder().id(1L).title("SSH Disconnected")
                .severity(Alert.AlertSeverity.CRITICAL).server(server).build();

        com.chronovault.entity.Integration slack = com.chronovault.entity.Integration.builder()
                .id(1L).active(true).type(com.chronovault.entity.Integration.IntegrationType.SLACK)
                .url("https://hooks.slack.com/test").user(testUser).build();
        com.chronovault.entity.Integration webhook = com.chronovault.entity.Integration.builder()
                .id(2L).active(true).type(com.chronovault.entity.Integration.IntegrationType.WEBHOOK)
                .url("https://example.com/webhook").user(testUser).build();

        when(integrationRepository.findByUserId(1L)).thenReturn(List.of(slack, webhook));

        alertService.escalateCriticalAlert(criticalAlert);

        // Should attempt to send to both integrations (HTTP calls will fail but no exception)
        verify(integrationRepository).findByUserId(1L);
    }

    @Test
    void escalateCriticalAlert_nonCritical_doesNothing() {
        Alert warningAlert = Alert.builder().id(1L).title("Disk Warning")
                .severity(Alert.AlertSeverity.WARNING).build();

        alertService.escalateCriticalAlert(warningAlert);

        verify(integrationRepository, never()).findByUserId(anyLong());
    }

    @Test
    void escalateCriticalAlert_noServerOwner_logsWarning() {
        Alert criticalAlert = Alert.builder().id(1L).title("Critical Alert")
                .severity(Alert.AlertSeverity.CRITICAL).server(null).build();

        alertService.escalateCriticalAlert(criticalAlert);

        verify(integrationRepository, never()).findByUserId(anyLong());
    }

    @Test
    void escalateCriticalAlert_skipsInactiveIntegrations() {
        Server server = Server.builder().id(1L).name("Test Server").user(testUser).build();
        Alert criticalAlert = Alert.builder().id(1L).title("SSH Disconnected")
                .severity(Alert.AlertSeverity.CRITICAL).server(server).build();

        com.chronovault.entity.Integration inactive = com.chronovault.entity.Integration.builder()
                .id(1L).active(false).type(com.chronovault.entity.Integration.IntegrationType.SLACK)
                .url("https://hooks.slack.com/test").user(testUser).build();

        when(integrationRepository.findByUserId(1L)).thenReturn(List.of(inactive));

        alertService.escalateCriticalAlert(criticalAlert);

        verify(integrationRepository).findByUserId(1L);
    }
}
