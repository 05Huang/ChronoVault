package com.chronovault.controller;

import com.chronovault.dto.settings.ApiKeyDTO;
import com.chronovault.dto.settings.AuditLogDTO;
import com.chronovault.dto.settings.GenerateKeyRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/api-keys")
    public ResponseEntity<ApiResponse<List<ApiKeyDTO>>> getApiKeys(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getApiKeys(auth.getName())));
    }

    @PostMapping("/api-keys")
    public ResponseEntity<ApiResponse<ApiKeyDTO>> generateKey(Authentication auth, @Valid @RequestBody GenerateKeyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(settingsService.generateKey(auth.getName(), request)));
    }

    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteKey(@PathVariable Long id) {
        settingsService.deleteKey(id);
        return ResponseEntity.ok(ApiResponse.success("密钥已删除", null));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLogDTO>>> getAuditLogs() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getAuditLogs()));
    }
}
