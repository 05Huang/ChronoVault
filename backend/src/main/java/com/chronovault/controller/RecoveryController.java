package com.chronovault.controller;

import com.chronovault.dto.recovery.*;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.RecoveryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recovery")
@RequiredArgsConstructor
@Tag(name = "Recovery", description = "恢复管理 — 模拟、执行、迁移")
public class RecoveryController {

    private final RecoveryService recoveryService;

    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<JobStatusDTO>> simulate(@Valid @RequestBody SimulateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(recoveryService.simulate(request)));
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<JobStatusDTO>> execute(@Valid @RequestBody ExecuteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(recoveryService.execute(request)));
    }

    @PostMapping("/migrate")
    public ResponseEntity<ApiResponse<JobStatusDTO>> migrate(@Valid @RequestBody MigrateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(recoveryService.migrate(request)));
    }
}
