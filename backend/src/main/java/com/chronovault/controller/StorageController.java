package com.chronovault.controller;

import com.chronovault.dto.storage.StorageDistributionDTO;
import com.chronovault.dto.storage.StorageHealthDTO;
import com.chronovault.dto.storage.StorageOverviewDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<List<StorageOverviewDTO>>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(storageService.getOverview()));
    }

    @GetMapping("/distribution")
    public ResponseEntity<ApiResponse<List<StorageDistributionDTO>>> getDistribution() {
        return ResponseEntity.ok(ApiResponse.success(storageService.getDistribution()));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<StorageHealthDTO>> getHealth() {
        return ResponseEntity.ok(ApiResponse.success(storageService.getHealth()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StorageOverviewDTO>> addTarget(Authentication auth, @RequestBody Map<String, Object> body) {
        StorageOverviewDTO target = storageService.addTarget(
                auth.getName(),
                (String) body.get("type"),
                (String) body.get("name"),
                (String) body.get("endpoint"),
                body.containsKey("totalBytes") ? ((Number) body.get("totalBytes")).longValue() : null
        );
        return ResponseEntity.ok(ApiResponse.success(target));
    }
}
