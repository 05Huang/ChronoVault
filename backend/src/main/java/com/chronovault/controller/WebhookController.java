package com.chronovault.controller;

import com.chronovault.audit.Auditable;
import com.chronovault.entity.WebhookDeliveryLog;
import com.chronovault.entity.WebhookEndpoint;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;

@RestController
@RequestMapping(ApiVersion.V1 + "/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Webhook管理 — 创建、测试、删除 webhook 端点")
public class WebhookController {

    private final WebhookService webhookService;

    @Operation(summary = "获取 Endpoints")
    @GetMapping
    public ResponseEntity<ApiResponse<List<WebhookEndpoint>>> getEndpoints() {
        return ResponseEntity.ok(ApiResponse.success(webhookService.getEndpoints()));
    }

    @Auditable(action = "创建 Webhook", changeType = "CONFIG_CHANGED", resourceType = "WEBHOOK")
    @Operation(summary = "操作 Endpoint")
    @PostMapping
    public ResponseEntity<ApiResponse<WebhookEndpoint>> createEndpoint(@RequestBody WebhookEndpoint endpoint) {
        WebhookEndpoint created = webhookService.createEndpoint(endpoint);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "webhooks/" + created.getId()))
                .body(ApiResponse.success(created));
    }

    @Auditable(action = "更新 Webhook", changeType = "CONFIG_CHANGED", resourceType = "WEBHOOK", resourceId = "#id")
    @Operation(summary = "更新 Endpoint")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WebhookEndpoint>> updateEndpoint(
            @PathVariable Long id,
            @RequestBody WebhookEndpoint endpoint) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "webhooks/" + id))
                .body(ApiResponse.success(webhookService.updateEndpoint(id, endpoint)));
    }

    @Auditable(action = "删除 Webhook", changeType = "CONFIG_CHANGED", resourceType = "WEBHOOK", resourceId = "#id")
    @Operation(summary = "删除 Endpoint")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEndpoint(@PathVariable Long id) {
        webhookService.deleteEndpoint(id);
        return ResponseEntity.ok(ApiResponse.successMsg("Webhook 已删除"));
    }

    @Operation(summary = "获取 Delivery Logs")
    @GetMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<List<WebhookDeliveryLog>>> getDeliveryLogs(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.getDeliveryLogs(id)));
    }

    @Operation(summary = "操作 Webhook")
    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<String>> testWebhook(@PathVariable Long id) {
        webhookService.deliverEvent("WEBHOOK_TEST", "{\"event\":\"test\",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
        return ResponseEntity.ok(ApiResponse.success("测试事件已发送"));
    }
}