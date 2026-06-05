package com.chronovault.controller;

import com.chronovault.audit.Auditable;
import com.chronovault.dto.alert.*;
import com.chronovault.dto.integration.IntegrationDTO;
import com.chronovault.dto.integration.CreateIntegrationRequest;
import com.chronovault.dto.integration.UpdateIntegrationRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;

@RestController
@RequestMapping(ApiVersion.V1 + "/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "告警管理 — 查看、确认、规则管理")
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @Operation(summary = "获取告警列表", description = "返回告警列表，支持分页和过滤。默认分页参数：page=0, size=20")
    public ResponseEntity<?> getAlerts(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AlertDTO> result = alertService.getAlertsPaged(filter, page, size);
        return ResponseEntity.ok(ApiResponse.successPage(
                result.getContent(), page, size, result.getTotalElements()));
    }

    @GetMapping("/stats")
    @Operation(summary = "获取告警统计", description = "返回告警数量统计（按级别、状态分组）")
    public ResponseEntity<ApiResponse<AlertStatsDTO>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(alertService.getStats()));
    }

    @PostMapping("/{id}/restart")
    @Operation(summary = "重启容器", description = "根据告警信息重启指定容器")
    public ResponseEntity<ApiResponse<Void>> restart(@PathVariable Long id) {
        alertService.restartContainer(id);
        return ResponseEntity.ok(ApiResponse.successMsg("容器重启成功"));
    }

    @PostMapping("/{id}/expand-storage")
    @Operation(summary = "扩展存储", description = "根据告警信息扩展存储空间")
    public ResponseEntity<ApiResponse<Void>> expandStorage(@PathVariable Long id) {
        alertService.expandStorage(id);
        return ResponseEntity.ok(ApiResponse.successMsg("存储扩展成功"));
    }

    @PostMapping("/{id}/rollback-config")
    @Operation(summary = "回滚配置", description = "根据告警信息回滚到上一个已知良好的配置版本")
    public ResponseEntity<ApiResponse<Void>> rollbackConfig(@PathVariable Long id) {
        alertService.rollbackConfig(id);
        return ResponseEntity.ok(ApiResponse.successMsg("配置回滚成功"));
    }

    @PostMapping("/{id}/dismiss")
    @Operation(summary = "忽略告警", description = "标记告警为已忽略，不再显示在活跃告警列表中")
    public ResponseEntity<ApiResponse<Void>> dismiss(@PathVariable Long id) {
        alertService.dismiss(id);
        return ResponseEntity.ok(ApiResponse.successMsg("告警已忽略"));
    }

    @GetMapping("/rules")
    @Operation(summary = "获取告警规则列表", description = "返回当前用户配置的所有告警规则")
    public ResponseEntity<ApiResponse<List<AlertRuleDTO>>> getRules(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(alertService.getRules(SecurityUtils.getCurrentUsername(auth))));
    }

    @Auditable(action = "创建告警规则", changeType = "CONFIG_CHANGED", resourceType = "ALERT_RULE")
    @PostMapping("/rules")
    @Operation(summary = "创建告警规则", description = "创建新的告警触发规则")
    public ResponseEntity<ApiResponse<AlertRuleDTO>> createRule(Authentication auth, @Valid @RequestBody CreateAlertRuleRequest request) {
        AlertRuleDTO rule = alertService.createRule(SecurityUtils.getCurrentUsername(auth), request);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "alerts/rules/" + rule.id()))
                .body(ApiResponse.success(rule));
    }

    // Integration endpoints
    @GetMapping("/integrations")
    @Operation(summary = "获取通知集成列表", description = "返回所有配置的通知集成（Slack、钉钉等）")
    public ResponseEntity<ApiResponse<List<IntegrationDTO>>> getIntegrations(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(alertService.getIntegrations(SecurityUtils.getCurrentUsername(auth))));
    }

    @PostMapping("/integrations")
    @Operation(summary = "创建通知集成", description = "配置新的通知渠道（Slack、钉钉、自定义 Webhook）")
    public ResponseEntity<ApiResponse<IntegrationDTO>> createIntegration(Authentication auth, @Valid @RequestBody CreateIntegrationRequest request) {
        IntegrationDTO integration = alertService.createIntegration(
                SecurityUtils.getCurrentUsername(auth), request.type(), request.name(), request.url());
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "alerts/integrations/" + integration.id()))
                .body(ApiResponse.success(integration));
    }

    @PutMapping("/integrations/{id}")
    @Operation(summary = "更新通知集成", description = "更新指定通知集成的配置（启用/禁用）")
    public ResponseEntity<ApiResponse<IntegrationDTO>> updateIntegration(
            @PathVariable Long id,
            @Valid @RequestBody UpdateIntegrationRequest body) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "alerts/integrations/" + id))
                .body(ApiResponse.success(alertService.updateIntegration(id, body.active())));
    }

    @Auditable(action = "删除告警规则", changeType = "CONFIG_CHANGED", resourceType = "ALERT_RULE", resourceId = "#id")
    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除告警规则", description = "删除指定的告警规则")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.successMsg("告警规则已删除"));
    }

    @Auditable(action = "删除集成", changeType = "CONFIG_CHANGED", resourceType = "INTEGRATION", resourceId = "#id")
    @DeleteMapping("/integrations/{id}")
    @Operation(summary = "删除通知集成", description = "删除指定的通知集成配置")
    public ResponseEntity<ApiResponse<Void>> deleteIntegration(@PathVariable Long id) {
        alertService.deleteIntegration(id);
        return ResponseEntity.ok(ApiResponse.successMsg("集成已删除"));
    }
}
