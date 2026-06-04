package com.chronovault.controller;

import com.chronovault.dto.risk.RiskDTO;
import com.chronovault.dto.risk.RiskNodeDTO;
import com.chronovault.dto.risk.RiskScoreDTO;
import com.chronovault.dto.risk.RiskTrendDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @Operation(summary = "获取 Score")
    @GetMapping("/score")
    public ResponseEntity<ApiResponse<RiskScoreDTO>> getScore() {
        return ResponseEntity.ok(ApiResponse.success(riskService.getScore()));
    }

    @Operation(summary = "获取 Trend")
    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<List<RiskTrendDTO>>> getTrend() {
        return ResponseEntity.ok(ApiResponse.success(riskService.getTrend()));
    }

    @Operation(summary = "获取 Nodes")
    @GetMapping("/nodes")
    public ResponseEntity<ApiResponse<List<RiskNodeDTO>>> getNodes() {
        return ResponseEntity.ok(ApiResponse.success(riskService.getNodes()));
    }

    @Operation(summary = "获取 Risks")
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<RiskDTO>>> getRisks() {
        return ResponseEntity.ok(ApiResponse.success(riskService.getRisks()));
    }

    @Operation(summary = "操作 mitigate")
    @PostMapping("/{id}/mitigate")
    public ResponseEntity<ApiResponse<Void>> mitigate(@PathVariable Long id) {
        riskService.mitigate(id);
        return ResponseEntity.ok(ApiResponse.successMsg("风险已缓解"));
    }

    @Operation(summary = "操作 scan")
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<List<RiskDTO>>> scan() {
        return ResponseEntity.ok(ApiResponse.success(riskService.scan()));
    }
}
