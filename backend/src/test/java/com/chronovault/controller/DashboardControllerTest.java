package com.chronovault.controller;

import com.chronovault.dto.dashboard.*;
import com.chronovault.dto.risk.RiskScoreDTO;
import com.chronovault.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock private DashboardService dashboardService;

    @InjectMocks
    private DashboardController controller;

    @Test
    void getStats_returnsStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO(5, 3, 10, 80, 50, 2, "100%", "1GB", "10GB", 3, 60.0);
        when(dashboardService.getStats()).thenReturn(stats);
        var response = controller.getStats();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void getAnomalies_returnsList() {
        when(dashboardService.getAnomalies()).thenReturn(List.of());
        var response = controller.getAnomalies();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getStorageSummary_returnsList() {
        when(dashboardService.getStorageSummary()).thenReturn(List.of());
        var response = controller.getStorageSummary();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getRiskScore_returnsScore() {
        RiskScoreDTO score = new RiskScoreDTO(85.0, "低风险", "系统运行良好", 0, 1, 0);
        when(dashboardService.getRiskScore()).thenReturn(score);
        var response = controller.getRiskScore();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getTopology_returnsTopology() {
        TopologyDTO topology = new TopologyDTO(List.of(), List.of());
        when(dashboardService.getTopology()).thenReturn(topology);
        var response = controller.getTopology();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getActivityTrend_returnsTrend() {
        when(dashboardService.getActivityTrend("24h")).thenReturn(List.of());
        var response = controller.getActivityTrend("24h");
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getOverview_returnsOverview() {
        DashboardOverviewDTO overview = new DashboardOverviewDTO(List.of(), List.of(),
                new PendingAlertsInfo(0, 0, 0), new RecentRollbackInfo(null, null, null));
        when(dashboardService.getOverview()).thenReturn(overview);
        var response = controller.getOverview();
        assertEquals(200, response.getStatusCode().value());
    }
}