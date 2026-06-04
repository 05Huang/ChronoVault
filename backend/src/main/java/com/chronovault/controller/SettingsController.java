package com.chronovault.controller;

import com.chronovault.dto.settings.ApiKeyDTO;
import com.chronovault.dto.settings.AuditLogDTO;
import com.chronovault.dto.settings.CreateApiKeyResponse;
import com.chronovault.dto.settings.GenerateKeyRequest;
import com.chronovault.dto.settings.UpdateAiConfigRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.chronovault.config.ApiVersion;

@RestController
@RequestMapping(ApiVersion.V1 + "/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "系统设置 — API密钥、审计日志、AI配置")
public class SettingsController {

    private final SettingsService settingsService;

    @Operation(summary = "获取 Api Keys")
    @GetMapping("/api-keys")
    public ResponseEntity<ApiResponse<List<ApiKeyDTO>>> getApiKeys(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getApiKeys(SecurityUtils.getCurrentUsername(auth))));
    }

    @Operation(summary = "操作 Key")
    @PostMapping("/api-keys")
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> generateKey(Authentication auth, @Valid @RequestBody GenerateKeyRequest request) {
        CreateApiKeyResponse response = settingsService.generateKey(SecurityUtils.getCurrentUsername(auth), request);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "settings/api-keys/" + response.apiKey().id()))
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "删除 Key")
    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteKey(@PathVariable Long id) {
        settingsService.deleteKey(id);
        return ResponseEntity.ok(ApiResponse.successMsg("密钥已删除"));
    }

    @Operation(summary = "获取 Audit Logs")
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLogDTO>>> getAuditLogs() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getAuditLogs()));
    }

    @GetMapping("/ai-config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAiConfig() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getAiConfig()));
    }

    @Operation(summary = "更新 Ai Config")
    @PutMapping("/ai-config")
    public ResponseEntity<ApiResponse<Void>> updateAiConfig(@Valid @RequestBody UpdateAiConfigRequest body) {
        settingsService.updateAiConfig(body.config());
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "settings/ai-config"))
                .body(ApiResponse.successMsg("AI 配置已更新"));
    }

    @Operation(summary = "获取 search Audit Logs")
    @GetMapping("/audit-logs/search")
    public ResponseEntity<?> searchAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AuditLogDTO> result = settingsService.searchAuditLogs(action, userId, since, until, page, size);
        return ResponseEntity.ok(ApiResponse.successPage(
                result.getContent(), page, size, result.getTotalElements()));
    }

    @Operation(summary = "导出审计日志", description = "导出审计日志为 CSV 格式，支持按时间范围筛选")
    @GetMapping("/audit-logs/export")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until) {
        java.util.List<java.util.Map<String, String>> data = settingsService.exportAuditLogs(since, until);

        StringBuilder csv = new StringBuilder();
        // Header
        csv.append("ID,用户,操作,变更类型,资源类型,资源ID,IP地址,创建时间\n");
        // Rows
        for (java.util.Map<String, String> row : data) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                    csvEscape(row.get("id")),
                    csvEscape(row.get("user")),
                    csvEscape(row.get("action")),
                    csvEscape(row.get("changeType")),
                    csvEscape(row.get("resourceType")),
                    csvEscape(row.get("resourceId")),
                    csvEscape(row.get("ipAddress")),
                    csvEscape(row.get("createdAt"))));
        }

        byte[] content = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit_logs.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(content.length)
                .body(content);
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
