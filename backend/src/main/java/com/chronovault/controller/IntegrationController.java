package com.chronovault.controller;

import com.chronovault.dto.integration.CreateIntegrationRequest;
import com.chronovault.dto.integration.IntegrationDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<IntegrationDTO>>> getIntegrations(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(alertService.getIntegrations(auth.getName())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IntegrationDTO>> createIntegration(Authentication auth, @Valid @RequestBody CreateIntegrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                alertService.createIntegration(auth.getName(), request.type(), request.name(), request.url())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IntegrationDTO>> updateIntegration(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Boolean active = body.containsKey("active") ? (Boolean) body.get("active") : null;
        return ResponseEntity.ok(ApiResponse.success(alertService.updateIntegration(id, active)));
    }
}
