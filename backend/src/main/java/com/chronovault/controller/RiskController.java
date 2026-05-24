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

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @GetMapping("/score")
    public ResponseEntity<ApiResponse<RiskScoreDTO>> getScore() {
        return ResponseEntity.ok(ApiResponse.success(riskService.getScore()));
    }

    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<List<RiskTrendDTO>>> getTrend() {
        return ResponseEntity.ok(ApiResponse.success(riskService.getTrend()));
    }

    @GetMapping("/nodes")
    public ResponseEntity<ApiResponse<List<RiskNodeDTO>>> getNodes() {
        return ResponseEntity.ok(ApiResponse.success(riskService.getNodes()));
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<RiskDTO>>> getRisks() {
        return ResponseEntity.ok(ApiResponse.success(riskService.getRisks()));
    }

    @PostMapping("/{id}/mitigate")
    public ResponseEntity<ApiResponse<Void>> mitigate(@PathVariable Long id) {
        riskService.mitigate(id);
        return ResponseEntity.ok(ApiResponse.successMsg("风险已缓解"));
    }

    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<List<RiskDTO>>> scan() {
        return ResponseEntity.ok(ApiResponse.success(riskService.scan()));
    }
}
