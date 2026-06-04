package com.chronovault.controller;

import com.chronovault.dto.integration.CreateIntegrationRequest;
import com.chronovault.dto.integration.IntegrationDTO;
import com.chronovault.dto.integration.UpdateIntegrationRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final AlertService alertService;

    @Operation(summary = "获取 Integrations")
    @GetMapping
    public ResponseEntity<ApiResponse<List<IntegrationDTO>>> getIntegrations(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(alertService.getIntegrations(SecurityUtils.getCurrentUsername(auth))));
    }

    @Operation(summary = "操作 Integration")
    @PostMapping
    public ResponseEntity<ApiResponse<IntegrationDTO>> createIntegration(Authentication auth, @Valid @RequestBody CreateIntegrationRequest request) {
        IntegrationDTO integration = alertService.createIntegration(
                SecurityUtils.getCurrentUsername(auth), request.type(), request.name(), request.url());
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "integrations/" + integration.id()))
                .body(ApiResponse.success(integration));
    }

    @Operation(summary = "更新 Integration")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IntegrationDTO>> updateIntegration(
            @PathVariable Long id,
            @Valid @RequestBody UpdateIntegrationRequest body) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "integrations/" + id))
                .body(ApiResponse.success(alertService.updateIntegration(id, body.active())));
    }
}
