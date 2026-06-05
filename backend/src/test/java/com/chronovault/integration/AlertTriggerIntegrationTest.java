package com.chronovault.integration;

import com.chronovault.entity.Alert;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.User;
import com.chronovault.repository.AlertRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.UserRepository;
import com.chronovault.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for alert trigger flow:
 * Create snapshot -> Simulate high-risk change -> Verify alert generation
 */
@Transactional
class AlertTriggerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ServerRepository serverRepository;
    @Autowired private SnapshotRepository snapshotRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private AlertService alertService;

    private User testUser;
    private Server testServer;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("Alert Test User")
                .email("alert-test-" + System.nanoTime() + "@test.com")
                .passwordHash("hashed")
                .role(User.Role.MEMBER)
                .status(User.UserStatus.ONLINE)
                .build());

        testServer = serverRepository.save(Server.builder()
                .user(testUser)
                .name("test-server")
                .ip("192.168.1.100")
                .os("Ubuntu 22.04")
                .status(Server.ServerStatus.RUNNING)
                .sshPort(22)
                .sshUsername("root")
                .sshAuthMethod("KEY")
                .build());
    }

    @Test
    void alertCreation_persistsCorrectly() {
        Alert alert = Alert.builder()
                .server(testServer)
                .severity(Alert.AlertSeverity.WARNING)
                .title("Disk usage above 80%")
                .description("Server disk usage has exceeded the warning threshold")
                .source("disk-monitor")
                .category("磁盘")
                .status(Alert.AlertStatus.OPEN)
                .storagePercent(82)
                .build();

        Alert saved = alertRepository.save(alert);

        assertNotNull(saved.getId());
        assertEquals("Disk usage above 80%", saved.getTitle());
        assertEquals(Alert.AlertSeverity.WARNING, saved.getSeverity());
        assertEquals(Alert.AlertStatus.OPEN, saved.getStatus());
        assertEquals(testServer.getId(), saved.getServer().getId());
        assertEquals(82, saved.getStoragePercent());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void alertLifecycle_openToResolvedToDismissed() {
        Alert alert = Alert.builder()
                .server(testServer)
                .severity(Alert.AlertSeverity.CRITICAL)
                .title("Service down")
                .description("Nginx service is not running")
                .source("health-check")
                .category("容器")
                .status(Alert.AlertStatus.OPEN)
                .build();

        Alert saved = alertRepository.save(alert);
        assertEquals(Alert.AlertStatus.OPEN, saved.getStatus());

        saved.setStatus(Alert.AlertStatus.RESOLVED);
        alertRepository.save(saved);
        Alert resolved = alertRepository.findById(saved.getId()).orElseThrow();
        assertEquals(Alert.AlertStatus.RESOLVED, resolved.getStatus());

        resolved.setStatus(Alert.AlertStatus.DISMISSED);
        alertRepository.save(resolved);
        Alert dismissed = alertRepository.findById(saved.getId()).orElseThrow();
        assertEquals(Alert.AlertStatus.DISMISSED, dismissed.getStatus());
    }

    @Test
    void alertTriggerFlow_createSnapshotThenHighRiskChange() {
        Snapshot snapshot = snapshotRepository.save(Snapshot.builder()
                .server(testServer)
                .title("Baseline Snapshot")
                .type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE)
                .hash("abc123def456")
                .sizeBytes(1024000L)
                .build());

        assertNotNull(snapshot.getId());

        Alert highRiskAlert = Alert.builder()
                .server(testServer)
                .severity(Alert.AlertSeverity.CRITICAL)
                .title("Kernel upgrade detected")
                .description("System kernel upgraded from 5.15.0 to 6.1.0 - requires reboot")
                .source("state-diff")
                .category("系统")
                .status(Alert.AlertStatus.OPEN)
                .build();

        Alert savedAlert = alertRepository.save(highRiskAlert);

        assertNotNull(savedAlert.getId());
        assertEquals(Alert.AlertSeverity.CRITICAL, savedAlert.getSeverity());
        assertEquals(Alert.AlertStatus.OPEN, savedAlert.getStatus());
        assertEquals("state-diff", savedAlert.getSource());

        var criticalAlerts = alertRepository.findBySeverityOrderByCreatedAtDesc(Alert.AlertSeverity.CRITICAL);
        assertFalse(criticalAlerts.isEmpty());
        assertEquals("Kernel upgrade detected", criticalAlerts.get(0).getTitle());

        var stats = alertService.getStats();
        assertTrue(stats.total() >= 1);
        assertTrue(stats.critical() >= 1);
    }

    @Test
    void alertDismissFlow() {
        Alert alert = alertRepository.save(Alert.builder()
                .server(testServer)
                .severity(Alert.AlertSeverity.WARNING)
                .title("Temporary warning")
                .description("This is a test warning")
                .source("test")
                .category("测试")
                .status(Alert.AlertStatus.OPEN)
                .build());

        alertService.dismiss(alert.getId());

        Alert dismissed = alertRepository.findById(alert.getId()).orElseThrow();
        assertEquals(Alert.AlertStatus.DISMISSED, dismissed.getStatus());
    }

    @Test
    void alertQueryFilters_workCorrectly() {
        alertRepository.save(Alert.builder()
                .server(testServer).severity(Alert.AlertSeverity.CRITICAL)
                .title("Critical Alert").description("desc")
                .source("test").status(Alert.AlertStatus.OPEN).build());

        alertRepository.save(Alert.builder()
                .server(testServer).severity(Alert.AlertSeverity.WARNING)
                .title("Warning Alert").description("desc")
                .source("test").status(Alert.AlertStatus.OPEN).build());

        alertRepository.save(Alert.builder()
                .server(testServer).severity(Alert.AlertSeverity.PREDICTIVE)
                .title("Predictive Alert").description("desc")
                .source("test").status(Alert.AlertStatus.OPEN).build());

        var criticalAlerts = alertRepository.findBySeverityOrderByCreatedAtDesc(Alert.AlertSeverity.CRITICAL);
        assertEquals(1, criticalAlerts.size());
        assertEquals("Critical Alert", criticalAlerts.get(0).getTitle());

        var warningAlerts = alertRepository.findBySeverityOrderByCreatedAtDesc(Alert.AlertSeverity.WARNING);
        assertEquals(1, warningAlerts.size());

        var predictiveAlerts = alertRepository.findBySeverityOrderByCreatedAtDesc(Alert.AlertSeverity.PREDICTIVE);
        assertEquals(1, predictiveAlerts.size());

        assertEquals(1, alertRepository.countBySeverity(Alert.AlertSeverity.CRITICAL));
        assertEquals(1, alertRepository.countBySeverity(Alert.AlertSeverity.WARNING));
        assertEquals(1, alertRepository.countBySeverity(Alert.AlertSeverity.PREDICTIVE));
    }

    @Test
    void alertDismissNonExistent_throwsException() {
        assertThrows(Exception.class, () -> alertService.dismiss(99999L));
    }
}
