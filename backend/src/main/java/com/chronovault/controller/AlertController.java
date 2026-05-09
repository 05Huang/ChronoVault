package com.chronovault.controller;

import com.chronovault.dto.alert.*;
import com.chronovault.dto.integration.IntegrationDTO;
import com.chronovault.dto.integration.CreateIntegrationRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AlertDTO>>> getAlerts(@RequestParam(required = false) String filter) {
        return ResponseEntity.ok(ApiResponse.success(alertService.getAlerts(filter)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AlertStatsDTO>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(alertService.getStats()));
    }

    @PostMapping("/{id}/restart")
    public ResponseEntity<ApiResponse<Void>> restart(@PathVariable Long id) {
        alertService.restartContainer(id);
        return ResponseEntity.ok(ApiResponse.success("容器重启成功", null));
    }

    @PostMapping("/{id}/expand-storage")
    public ResponseEntity<ApiResponse<Void>> expandStorage(@PathVariable Long id) {
        alertService.expandStorage(id);
        return ResponseEntity.ok(ApiResponse.success("存储扩展成功", null));
    }

    @PostMapping("/{id}/rollback-config")
    public ResponseEntity<ApiResponse<Void>> rollbackConfig(@PathVariable Long id) {
        alertService.rollbackConfig(id);
        return ResponseEntity.ok(ApiResponse.success("配置回滚成功", null));
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismiss(@PathVariable Long id) {
        alertService.dismiss(id);
        return ResponseEntity.ok(ApiResponse.success("告警已忽略", null));
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
    public ResponseEntity<ApiResponse<IntegrationDTO>> updateIntegration(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        Boolean active = body.containsKey("active") ? (Boolean) body.get("active") : null;
        return ResponseEntity.ok(ApiResponse.success(alertService.updateIntegration(id, active)));
    }
}
