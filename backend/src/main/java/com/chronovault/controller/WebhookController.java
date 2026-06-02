package com.chronovault.controller;

import com.chronovault.entity.WebhookDeliveryLog;
import com.chronovault.entity.WebhookEndpoint;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WebhookEndpoint>>> getEndpoints() {
        return ResponseEntity.ok(ApiResponse.success(webhookService.getEndpoints()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WebhookEndpoint>> createEndpoint(@RequestBody WebhookEndpoint endpoint) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.createEndpoint(endpoint)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WebhookEndpoint>> updateEndpoint(
            @PathVariable Long id,
            @RequestBody WebhookEndpoint endpoint) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.updateEndpoint(id, endpoint)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEndpoint(@PathVariable Long id) {
        webhookService.deleteEndpoint(id);
        return ResponseEntity.ok(ApiResponse.successMsg("Webhook 已删除"));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<List<WebhookDeliveryLog>>> getDeliveryLogs(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.getDeliveryLogs(id)));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<String>> testWebhook(@PathVariable Long id) {
        webhookService.deliverEvent("WEBHOOK_TEST", "{\"event\":\"test\",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
        return ResponseEntity.ok(ApiResponse.success("测试事件已发送"));
    }
}