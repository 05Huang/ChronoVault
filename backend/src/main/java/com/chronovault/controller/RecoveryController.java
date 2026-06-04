package com.chronovault.controller;

import com.chronovault.dto.recovery.*;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.RecoveryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/recovery")
@RequiredArgsConstructor
@Tag(name = "Recovery", description = "恢复管理 — 模拟、执行、迁移")
public class RecoveryController {

    private final RecoveryService recoveryService;

    @Operation(summary = "操作 simulate")
    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<JobStatusDTO>> simulate(@Valid @RequestBody SimulateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(recoveryService.simulate(request)));
    }

    @Operation(summary = "操作 execute")
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<JobStatusDTO>> execute(@Valid @RequestBody ExecuteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(recoveryService.execute(request)));
    }

    @Operation(summary = "操作 migrate")
    @PostMapping("/migrate")
    public ResponseEntity<ApiResponse<JobStatusDTO>> migrate(@Valid @RequestBody MigrateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(recoveryService.migrate(request)));
    }
}
