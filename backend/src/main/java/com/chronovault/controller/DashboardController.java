package com.chronovault.controller;

import com.chronovault.dto.dashboard.*;
import com.chronovault.dto.risk.RiskScoreDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取 Stats")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getStats()));
    }

    @Operation(summary = "获取 Anomalies")
    @GetMapping("/anomalies")
    public ResponseEntity<ApiResponse<List<AnomalyDTO>>> getAnomalies() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getAnomalies()));
    }

    @Operation(summary = "获取 Storage Summary")
    @GetMapping("/storage-summary")
    public ResponseEntity<ApiResponse<List<StorageSummaryDTO>>> getStorageSummary() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getStorageSummary()));
    }

    @Operation(summary = "获取 Risk Score")
    @GetMapping("/risk-score")
    public ResponseEntity<ApiResponse<RiskScoreDTO>> getRiskScore() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getRiskScore()));
    }

    @Operation(summary = "获取 Topology")
    @GetMapping("/topology")
    public ResponseEntity<ApiResponse<TopologyDTO>> getTopology() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getTopology()));
    }

    @Operation(summary = "获取 Activity Trend")
    @GetMapping("/activity-trend")
    public ResponseEntity<ApiResponse<List<ActivityTrendDTO>>> getActivityTrend(
            @RequestParam(defaultValue = "7d") String range) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getActivityTrend(range)));
    }

    @Operation(summary = "获取 Overview")
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<DashboardOverviewDTO>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getOverview()));
    }
}
