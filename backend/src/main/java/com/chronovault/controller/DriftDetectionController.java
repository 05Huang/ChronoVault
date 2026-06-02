package com.chronovault.controller;

import com.chronovault.dto.drift.DriftReportDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.DriftDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class DriftDetectionController {

    private final DriftDetectionService driftService;

    @GetMapping("/{id}/drift")
    public ResponseEntity<ApiResponse<DriftReportDTO>> detectDrift(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(driftService.detectDrift(id)));
    }
}