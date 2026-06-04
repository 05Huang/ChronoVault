package com.chronovault.controller;

import com.chronovault.dto.ai.*;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @Operation(summary = "获取 Insights")
    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<List<AiInsightDTO>>> getInsights() {
        return ResponseEntity.ok(ApiResponse.success(aiService.getInsights()));
    }

    @Operation(summary = "获取 Recommendations")
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<AiRecommendationDTO>>> getRecommendations() {
        return ResponseEntity.ok(ApiResponse.success(aiService.getRecommendations()));
    }

    @Operation(summary = "操作 apply Recommendation")
    @PostMapping("/recommendations/{id}/apply")
    public ResponseEntity<ApiResponse<Void>> applyRecommendation(@PathVariable Long id) {
        aiService.applyRecommendation(id);
        return ResponseEntity.ok(ApiResponse.successMsg("建议已应用"));
    }

    @Operation(summary = "获取 Risk Radar")
    @GetMapping("/risk-radar")
    public ResponseEntity<ApiResponse<RiskRadarDTO>> getRiskRadar() {
        return ResponseEntity.ok(ApiResponse.success(aiService.getRiskRadar()));
    }

    @GetMapping("/storage-prediction")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStoragePrediction() {
        return ResponseEntity.ok(ApiResponse.success(aiService.getStoragePrediction()));
    }

    @Operation(summary = "操作 Report")
    @PostMapping("/generate-report")
    public ResponseEntity<ApiResponse<String>> generateReport() {
        return ResponseEntity.ok(ApiResponse.success(aiService.generateReport()));
    }

    @Operation(summary = "获取 analyze Server")
    @GetMapping("/server-analysis/{serverId}")
    public ResponseEntity<ApiResponse<ServerAnalysisDTO>> analyzeServer(@PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(aiService.analyzeServer(serverId)));
    }
}
