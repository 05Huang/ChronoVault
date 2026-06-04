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
}
