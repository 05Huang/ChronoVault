package com.chronovault.controller;

import com.chronovault.dto.dashboard.*;
import com.chronovault.dto.risk.RiskScoreDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getStats()));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<ApiResponse<List<AnomalyDTO>>> getAnomalies() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getAnomalies()));
    }

    @GetMapping("/storage-summary")
    public ResponseEntity<ApiResponse<List<StorageSummaryDTO>>> getStorageSummary() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getStorageSummary()));
    }

    @GetMapping("/risk-score")
    public ResponseEntity<ApiResponse<RiskScoreDTO>> getRiskScore() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getRiskScore()));
    }

    @GetMapping("/topology")
    public ResponseEntity<ApiResponse<TopologyDTO>> getTopology() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getTopology()));
    }

    @GetMapping("/activity-trend")
    public ResponseEntity<ApiResponse<List<ActivityTrendDTO>>> getActivityTrend(
            @RequestParam(defaultValue = "7d") String range) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getActivityTrend(range)));
    }

    @GetMapping("/overview")
    @io.swagger.v3.oas.annotations.Operation(summary = "Dashboard 总览", description = "单一接口返回所有 Dashboard 数据，避免前端多次请求")
    public ResponseEntity<ApiResponse<DashboardOverviewDTO>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getOverview()));
    }
}
