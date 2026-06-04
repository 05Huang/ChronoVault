package com.chronovault.controller;

import com.chronovault.entity.SnapshotHook;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.SnapshotHookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/servers/{serverId}/hooks")
@RequiredArgsConstructor
public class SnapshotHookController {

    private final SnapshotHookService hookService;

    @Operation(summary = "获取 Hooks")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SnapshotHook>>> getHooks(@PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(hookService.getHooks(serverId)));
    }

    @Operation(summary = "操作 Hook")
    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotHook>> createHook(
            @PathVariable Long serverId,
            @RequestBody SnapshotHook hook) {
        SnapshotHook created = hookService.createHook(serverId, hook);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "servers/" + serverId + "/hooks/" + created.getId()))
                .body(ApiResponse.success(created));
    }

    @Operation(summary = "更新 Hook")
    @PutMapping("/{hookId}")
    public ResponseEntity<ApiResponse<SnapshotHook>> updateHook(
            @PathVariable Long serverId,
            @PathVariable Long hookId,
            @RequestBody SnapshotHook hook) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "servers/" + serverId + "/hooks/" + hookId))
                .body(ApiResponse.success(hookService.updateHook(serverId, hookId, hook)));
    }

    @Operation(summary = "删除 Hook")
    @DeleteMapping("/{hookId}")
    public ResponseEntity<ApiResponse<Void>> deleteHook(
            @PathVariable Long serverId,
            @PathVariable Long hookId) {
        hookService.deleteHook(serverId, hookId);
        return ResponseEntity.ok(ApiResponse.successMsg("Hook 已删除"));
    }
}