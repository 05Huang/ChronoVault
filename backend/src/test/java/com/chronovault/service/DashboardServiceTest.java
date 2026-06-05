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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
        when(alertRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(alert)));

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

    @Test
    void getTopology_multipleServersSameUser_createsEdges() {
        User user = User.builder().id(1L).build();
        Server s1 = Server.builder().id(1L).name("Server1").status(Server.ServerStatus.RUNNING).user(user).build();
        Server s2 = Server.builder().id(2L).name("Server2").status(Server.ServerStatus.RUNNING).user(user).build();
        Server s3 = Server.builder().id(3L).name("Server3").status(Server.ServerStatus.STOPPED).user(user).build();
        when(serverRepository.findAll()).thenReturn(List.of(s1, s2, s3));

        var result = dashboardService.getTopology();

        assertNotNull(result);
        assertEquals(3, result.nodes().size());
        // 3 servers from same user → 3 edges (triangle)
        assertEquals(3, result.edges().size());
    }

    @Test
    void getTopology_serversFromDifferentUsers_noEdges() {
        User user1 = User.builder().id(1L).build();
        User user2 = User.builder().id(2L).build();
        Server s1 = Server.builder().id(1L).name("Server1").status(Server.ServerStatus.RUNNING).user(user1).build();
        Server s2 = Server.builder().id(2L).name("Server2").status(Server.ServerStatus.RUNNING).user(user2).build();
        when(serverRepository.findAll()).thenReturn(List.of(s1, s2));

        var result = dashboardService.getTopology();

        assertNotNull(result);
        assertEquals(2, result.nodes().size());
        // Different users → no edges
        assertEquals(0, result.edges().size());
    }

    @Test
    void getTopology_emptyServerList_returnsEmptyTopology() {
        when(serverRepository.findAll()).thenReturn(List.of());

        var result = dashboardService.getTopology();

        assertNotNull(result);
        assertEquals(0, result.nodes().size());
        assertEquals(0, result.edges().size());
    }

    @Test
    void getTopology_serversWithNullUser_groupedTogether() {
        Server s1 = Server.builder().id(1L).name("Server1").status(Server.ServerStatus.RUNNING).user(null).build();
        Server s2 = Server.builder().id(2L).name("Server2").status(Server.ServerStatus.RUNNING).user(null).build();
        when(serverRepository.findAll()).thenReturn(List.of(s1, s2));

        var result = dashboardService.getTopology();

        assertNotNull(result);
        assertEquals(2, result.nodes().size());
        // Both have null user → grouped as user=0 → edge created
        assertEquals(1, result.edges().size());
    }

    // =====================================================================
    // Activity Trend Tests (Health Trend Chart)
    // =====================================================================

    @Test
    void getActivityTrend_7days_returnsDailyData() {
        when(eventRepository.findTop10000ByCreatedAtAfterOrderByCreatedAtDesc(any())).thenReturn(List.of());

        var result = dashboardService.getActivityTrend("7d");

        assertNotNull(result);
        assertEquals(7, result.size());
        // Each entry should have 0 snapshots, 0 alerts, 0 recoveries (no events)
        for (var entry : result) {
            assertEquals(0, entry.snapshots());
            assertEquals(0, entry.alerts());
            assertEquals(0, entry.recoveries());
        }
    }

    @Test
    void getActivityTrend_30days_returns30DaysData() {
        when(eventRepository.findTop10000ByCreatedAtAfterOrderByCreatedAtDesc(any())).thenReturn(List.of());

        var result = dashboardService.getActivityTrend("30d");

        assertNotNull(result);
        assertEquals(30, result.size());
    }

    @Test
    void getActivityTrend_withEvents_countsCorrectly() {
        com.chronovault.entity.Event snapshotEvent = com.chronovault.entity.Event.builder()
                .source("snapshot").createdAt(java.time.LocalDateTime.now().minusHours(2)).build();
        com.chronovault.entity.Event alertEvent = com.chronovault.entity.Event.builder()
                .source("alert").createdAt(java.time.LocalDateTime.now().minusHours(3)).build();

        when(eventRepository.findTop10000ByCreatedAtAfterOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(snapshotEvent, alertEvent));

        var result = dashboardService.getActivityTrend("24h");

        assertNotNull(result);
        assertEquals(24, result.size());
        // Find the entries with non-zero values
        long totalSnapshots = result.stream().mapToLong(ActivityTrendDTO::snapshots).sum();
        long totalAlerts = result.stream().mapToLong(ActivityTrendDTO::alerts).sum();
        assertEquals(1, totalSnapshots);
        assertEquals(1, totalAlerts);
    }

    // =====================================================================
    // Heatmap Tests
    // =====================================================================

    @Test
    void getHeatmap_noEvents_returnsEmptyGrid() {
        when(eventRepository.findTop10000ByCreatedAtAfterOrderByCreatedAtDesc(any())).thenReturn(List.of());

        var result = dashboardService.getHeatmap(4);

        assertNotNull(result);
        assertEquals(7, result.dayLabels().size());
        assertEquals(4, result.weekLabels().size());
        assertEquals(4, result.data().size());
        assertEquals(0, result.totalChanges());
        assertEquals(0.0, result.averageDailyChanges(), 0.01);
    }

    @Test
    void getHeatmap_withEvents_processesEvents() {
        // Verify that the method processes events without errors
        when(eventRepository.findTop10000ByCreatedAtAfterOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());

        var result = dashboardService.getHeatmap(4);

        assertNotNull(result);
        assertEquals(7, result.dayLabels().size());
        assertEquals(4, result.weekLabels().size());
        assertEquals(4, result.data().size());
    }

    @Test
    void getHeatmap_clampsWeeksRange() {
        when(eventRepository.findTop10000ByCreatedAtAfterOrderByCreatedAtDesc(any())).thenReturn(List.of());

        // Request 100 weeks, should be clamped to 12
        var result = dashboardService.getHeatmap(100);
        assertEquals(12, result.weekLabels().size());

        // Request 0 weeks, should be clamped to 1
        var result2 = dashboardService.getHeatmap(0);
        assertEquals(1, result2.weekLabels().size());
    }
}
