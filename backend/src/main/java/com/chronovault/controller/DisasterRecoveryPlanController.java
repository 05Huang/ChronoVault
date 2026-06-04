package com.chronovault.controller;

import com.chronovault.entity.DisasterRecoveryPlan;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.DisasterRecoveryPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/disaster-recovery")
@RequiredArgsConstructor
public class DisasterRecoveryPlanController {

    private final DisasterRecoveryPlanService planService;

    @Operation(summary = "获取 Plans")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DisasterRecoveryPlan>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlans()));
    }

    @Operation(summary = "获取 Plan")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DisasterRecoveryPlan>> getPlan(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlan(id)));
    }

    @Operation(summary = "操作 Plan")
    @PostMapping
    public ResponseEntity<ApiResponse<DisasterRecoveryPlan>> createPlan(@RequestBody DisasterRecoveryPlan plan) {
        DisasterRecoveryPlan created = planService.createPlan(plan);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "disaster-recovery/" + created.getId()))
                .body(ApiResponse.success(created));
    }

    @Operation(summary = "更新 Plan")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DisasterRecoveryPlan>> updatePlan(
            @PathVariable Long id,
            @RequestBody DisasterRecoveryPlan plan) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "disaster-recovery/" + id))
                .body(ApiResponse.success(planService.updatePlan(id, plan)));
    }

    @Operation(summary = "删除 Plan")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable Long id) {
        planService.deletePlan(id);
        return ResponseEntity.ok(ApiResponse.successMsg("恢复计划已删除"));
    }

    @Operation(summary = "操作 Plan")
    @PostMapping("/{id}/execute")
    public ResponseEntity<ApiResponse<DisasterRecoveryPlan>> executePlan(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(planService.executePlan(id)));
    }
}