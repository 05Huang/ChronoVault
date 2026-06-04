package com.chronovault.controller;

import com.chronovault.audit.Auditable;
import com.chronovault.dto.storage.CreateStorageRequest;
import com.chronovault.dto.storage.StorageDistributionDTO;
import com.chronovault.dto.storage.StorageHealthDTO;
import com.chronovault.dto.storage.StorageOverviewDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;

@RestController
@RequestMapping(ApiVersion.V1 + "/storage")
@RequiredArgsConstructor
@Tag(name = "Storage", description = "存储管理 — 添加、查看、删除存储目标")
public class StorageController {

    private final StorageService storageService;

    @GetMapping("/overview")
    @Operation(summary = "获取存储概览", description = "返回所有存储目标的使用概览")
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

    @Auditable(action = "添加存储目标", changeType = "CONFIG_CHANGED", resourceType = "STORAGE")
    @PostMapping
    public ResponseEntity<ApiResponse<StorageOverviewDTO>> addTarget(Authentication auth, @Valid @RequestBody CreateStorageRequest request) {
        StorageOverviewDTO target = storageService.addTarget(
                SecurityUtils.getCurrentUsername(auth),
                request.type(),
                request.name(),
                request.endpoint(),
                request.totalBytes(),
                request.accessKey(),
                request.secretKey(),
                request.region(),
                request.bucket()
        );
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "storage/" + target.id()))
                .body(ApiResponse.success(target));
    }

    @Auditable(action = "删除存储目标", changeType = "CONFIG_CHANGED", resourceType = "STORAGE", resourceId = "#id")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTarget(@PathVariable Long id) {
        storageService.deleteTarget(id);
        return ResponseEntity.ok(ApiResponse.successMsg("存储目标已删除"));
    }
}
