package com.chronovault.controller;

import com.chronovault.dto.alert.*;
import com.chronovault.dto.integration.IntegrationDTO;
import com.chronovault.dto.integration.CreateIntegrationRequest;
import com.chronovault.dto.integration.UpdateIntegrationRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "告警管理 — 查看、确认、规则管理")
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @Operation(summary = "获取告警列表", description = "返回告警列表，支持分页和过滤")
    public ResponseEntity<?> getAlerts(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            Page<AlertDTO> result = alertService.getAlertsPaged(filter, page, size);
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        return ResponseEntity.ok(ApiResponse.success(alertService.getAlerts(filter)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AlertStatsDTO>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(alertService.getStats()));
    }

    @PostMapping("/{id}/restart")
    public ResponseEntity<ApiResponse<Void>> restart(@PathVariable Long id) {
        alertService.restartContainer(id);
        return ResponseEntity.ok(ApiResponse.successMsg("容器重启成功"));
    }

    @PostMapping("/{id}/expand-storage")
    public ResponseEntity<ApiResponse<Void>> expandStorage(@PathVariable Long id) {
        alertService.expandStorage(id);
        return ResponseEntity.ok(ApiResponse.successMsg("存储扩展成功"));
    }

    @PostMapping("/{id}/rollback-config")
    public ResponseEntity<ApiResponse<Void>> rollbackConfig(@PathVariable Long id) {
        alertService.rollbackConfig(id);
        return ResponseEntity.ok(ApiResponse.successMsg("配置回滚成功"));
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismiss(@PathVariable Long id) {
        alertService.dismiss(id);
        return ResponseEntity.ok(ApiResponse.successMsg("告警已忽略"));
    }

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<AlertRuleDTO>>> getRules(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(alertService.getRules(auth.getName())));
    }

    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<AlertRuleDTO>> createRule(Authentication auth, @Valid @RequestBody CreateAlertRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(alertService.createRule(auth.getName(), request)));
    }

    // Integration endpoints
    @GetMapping("/integrations")
    public ResponseEntity<ApiResponse<List<IntegrationDTO>>> getIntegrations(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(alertService.getIntegrations(auth.getName())));
    }

    @PostMapping("/integrations")
    public ResponseEntity<ApiResponse<IntegrationDTO>> createIntegration(Authentication auth, @Valid @RequestBody CreateIntegrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                alertService.createIntegration(auth.getName(), request.type(), request.name(), request.url())));
    }

    @PutMapping("/integrations/{id}")
    public ResponseEntity<ApiResponse<IntegrationDTO>> updateIntegration(
            @PathVariable Long id,
            @Valid @RequestBody UpdateIntegrationRequest body) {
        return ResponseEntity.ok(ApiResponse.success(alertService.updateIntegration(id, body.active())));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.successMsg("告警规则已删除"));
    }

    @DeleteMapping("/integrations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteIntegration(@PathVariable Long id) {
        alertService.deleteIntegration(id);
        return ResponseEntity.ok(ApiResponse.successMsg("集成已删除"));
    }
}
