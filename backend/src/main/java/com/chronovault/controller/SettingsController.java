package com.chronovault.controller;

import com.chronovault.dto.settings.ApiKeyDTO;
import com.chronovault.dto.settings.AuditLogDTO;
import com.chronovault.dto.settings.CreateApiKeyResponse;
import com.chronovault.dto.settings.GenerateKeyRequest;
import com.chronovault.dto.settings.UpdateAiConfigRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "系统设置 — API密钥、审计日志、AI配置")
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/api-keys")
    public ResponseEntity<ApiResponse<List<ApiKeyDTO>>> getApiKeys(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getApiKeys(auth.getName())));
    }

    @PostMapping("/api-keys")
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> generateKey(Authentication auth, @Valid @RequestBody GenerateKeyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(settingsService.generateKey(auth.getName(), request)));
    }

    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteKey(@PathVariable Long id) {
        settingsService.deleteKey(id);
        return ResponseEntity.ok(ApiResponse.successMsg("密钥已删除"));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLogDTO>>> getAuditLogs() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getAuditLogs()));
    }

    @GetMapping("/ai-config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAiConfig() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getAiConfig()));
    }

    @PutMapping("/ai-config")
    public ResponseEntity<ApiResponse<Void>> updateAiConfig(@Valid @RequestBody UpdateAiConfigRequest body) {
        settingsService.updateAiConfig(body.config());
        return ResponseEntity.ok(ApiResponse.successMsg("AI 配置已更新"));
    }

    @GetMapping("/audit-logs/search")
    public ResponseEntity<ApiResponse<Page<AuditLogDTO>>> searchAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(settingsService.searchAuditLogs(action, userId, since, until, page, size)));
    }
}
