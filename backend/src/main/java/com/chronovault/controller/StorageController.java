package com.chronovault.controller;

import com.chronovault.dto.storage.CreateStorageRequest;
import com.chronovault.dto.storage.StorageDistributionDTO;
import com.chronovault.dto.storage.StorageHealthDTO;
import com.chronovault.dto.storage.StorageOverviewDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<ApiResponse<StorageOverviewDTO>> addTarget(Authentication auth, @Valid @RequestBody CreateStorageRequest request) {
        StorageOverviewDTO target = storageService.addTarget(
                auth.getName(),
                request.type(),
                request.name(),
                request.endpoint(),
                request.totalBytes(),
                request.accessKey(),
                request.secretKey(),
                request.region(),
                request.bucket()
        );
        return ResponseEntity.ok(ApiResponse.success(target));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTarget(@PathVariable Long id) {
        storageService.deleteTarget(id);
        return ResponseEntity.ok(ApiResponse.successMsg("存储目标已删除"));
    }
}
