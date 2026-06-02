package com.chronovault.service;

import com.chronovault.dto.dashboard.*;
import com.chronovault.entity.Server;
import com.chronovault.entity.User;
import com.chronovault.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private ServerRepository serverRepository;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private ContainerRepository containerRepository;
    @Mock private RiskRepository riskRepository;
    @Mock private EventRepository eventRepository;
    @Mock private com.chronovault.cache.CacheService cacheService;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void getStats_returnsDashboardStats() {
        when(serverRepository.count()).thenReturn(10L);
        when(serverRepository.countByStatus(Server.ServerStatus.RUNNING)).thenReturn(8L);
        when(containerRepository.count()).thenReturn(20L);
        when(snapshotRepository.count()).thenReturn(50L);
        when(snapshotRepository.countToday()).thenReturn(5L);
        when(alertRepository.countToday()).thenReturn(2L);
        when(storageTargetRepository.sumUsedBytes()).thenReturn(1024L * 1024L);
        when(storageTargetRepository.sumTotalBytes()).thenReturn(2048L * 1024L);
        when(teamMemberRepository.count()).thenReturn(3L);

        DashboardStatsDTO stats = dashboardService.getStats();

        assertNotNull(stats);
        assertEquals(10, stats.totalServers());
        assertEquals(8, stats.activeServers());
        assertEquals(20, stats.totalContainers());
        assertEquals(5, stats.todayBackups());
    }

    @Test
    void getAnomalies_returnsAlerts() {
        com.chronovault.entity.Alert alert = com.chronovault.entity.Alert.builder()
                .id(1L).title("CPU High").severity(com.chronovault.entity.Alert.AlertSeverity.CRITICAL)
                .source("nginx").createdAt(java.time.LocalDateTime.now()).build();
        when(alertRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(alert));

        var result = dashboardService.getAnomalies();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CPU High", result.get(0).title());
    }

    @Test
    void getStorageSummary_returnsStorageData() {
        com.chronovault.entity.StorageTarget target = com.chronovault.entity.StorageTarget.builder()
                .id(1L).type(com.chronovault.entity.StorageTarget.StorageType.LOCAL).name("Local")
                .usedBytes(500L).totalBytes(1000L).build();
        when(storageTargetRepository.findAll()).thenReturn(List.of(target));

        var result = dashboardService.getStorageSummary();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("LOCAL", result.get(0).type());
    }

    @Test
    void getRiskScore_returnsLowRisk() {
        when(riskRepository.countByLevel(com.chronovault.entity.Risk.RiskLevel.CRITICAL)).thenReturn(0L);
        when(riskRepository.countByLevel(com.chronovault.entity.Risk.RiskLevel.WARNING)).thenReturn(0L);
        when(riskRepository.countByLevel(com.chronovault.entity.Risk.RiskLevel.ANOMALOUS)).thenReturn(0L);

        var result = dashboardService.getRiskScore();

        assertNotNull(result);
        assertEquals("低风险", result.level());
    }

    @Test
    void getRiskScore_withCritical_returnsHighRisk() {
        when(riskRepository.countByLevel(com.chronovault.entity.Risk.RiskLevel.CRITICAL)).thenReturn(3L);
        when(riskRepository.countByLevel(com.chronovault.entity.Risk.RiskLevel.WARNING)).thenReturn(0L);
        when(riskRepository.countByLevel(com.chronovault.entity.Risk.RiskLevel.ANOMALOUS)).thenReturn(0L);

        var result = dashboardService.getRiskScore();

        assertNotNull(result);
        assertEquals("高风险", result.level());
    }

    @Test
    void getTopology_returnsServerNodes() {
        User user = User.builder().id(1L).build();
        Server server = Server.builder().id(1L).name("Server1").status(Server.ServerStatus.RUNNING).user(user).build();
        when(serverRepository.findAll()).thenReturn(List.of(server));

        var result = dashboardService.getTopology();

        assertNotNull(result);
        assertEquals(1, result.nodes().size());
    }
}
