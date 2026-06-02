package com.chronovault.controller;

import com.chronovault.entity.DisasterRecoveryPlan;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.DisasterRecoveryPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/disaster-recovery")
@RequiredArgsConstructor
public class DisasterRecoveryPlanController {

    private final DisasterRecoveryPlanService planService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DisasterRecoveryPlan>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlans()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DisasterRecoveryPlan>> getPlan(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlan(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DisasterRecoveryPlan>> createPlan(@RequestBody DisasterRecoveryPlan plan) {
        return ResponseEntity.ok(ApiResponse.success(planService.createPlan(plan)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DisasterRecoveryPlan>> updatePlan(
            @PathVariable Long id,
            @RequestBody DisasterRecoveryPlan plan) {
        return ResponseEntity.ok(ApiResponse.success(planService.updatePlan(id, plan)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable Long id) {
        planService.deletePlan(id);
        return ResponseEntity.ok(ApiResponse.successMsg("恢复计划已删除"));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<ApiResponse<DisasterRecoveryPlan>> executePlan(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(planService.executePlan(id)));
    }
}