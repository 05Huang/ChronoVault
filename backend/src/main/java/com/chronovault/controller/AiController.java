package com.chronovault.controller;

import com.chronovault.dto.ai.*;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<List<AiInsightDTO>>> getInsights() {
        return ResponseEntity.ok(ApiResponse.success(aiService.getInsights()));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<AiRecommendationDTO>>> getRecommendations() {
        return ResponseEntity.ok(ApiResponse.success(aiService.getRecommendations()));
    }

    @PostMapping("/recommendations/{id}/apply")
    public ResponseEntity<ApiResponse<Void>> applyRecommendation(@PathVariable Long id) {
        aiService.applyRecommendation(id);
        return ResponseEntity.ok(ApiResponse.successMsg("建议已应用"));
    }

    @GetMapping("/risk-radar")
    public ResponseEntity<ApiResponse<RiskRadarDTO>> getRiskRadar() {
        return ResponseEntity.ok(ApiResponse.success(aiService.getRiskRadar()));
    }

    @GetMapping("/storage-prediction")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStoragePrediction() {
        return ResponseEntity.ok(ApiResponse.success(aiService.getStoragePrediction()));
    }

    @PostMapping("/generate-report")
    public ResponseEntity<ApiResponse<String>> generateReport() {
        return ResponseEntity.ok(ApiResponse.success(aiService.generateReport()));
    }

    @GetMapping("/server-analysis/{serverId}")
    public ResponseEntity<ApiResponse<ServerAnalysisDTO>> analyzeServer(@PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(aiService.analyzeServer(serverId)));
    }
}
