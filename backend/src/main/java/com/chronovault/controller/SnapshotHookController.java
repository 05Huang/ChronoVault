package com.chronovault.controller;

import com.chronovault.entity.SnapshotHook;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.SnapshotHookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servers/{serverId}/hooks")
@RequiredArgsConstructor
public class SnapshotHookController {

    private final SnapshotHookService hookService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SnapshotHook>>> getHooks(@PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(hookService.getHooks(serverId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotHook>> createHook(
            @PathVariable Long serverId,
            @RequestBody SnapshotHook hook) {
        return ResponseEntity.ok(ApiResponse.success(hookService.createHook(serverId, hook)));
    }

    @PutMapping("/{hookId}")
    public ResponseEntity<ApiResponse<SnapshotHook>> updateHook(
            @PathVariable Long serverId,
            @PathVariable Long hookId,
            @RequestBody SnapshotHook hook) {
        return ResponseEntity.ok(ApiResponse.success(hookService.updateHook(serverId, hookId, hook)));
    }

    @DeleteMapping("/{hookId}")
    public ResponseEntity<ApiResponse<Void>> deleteHook(
            @PathVariable Long serverId,
            @PathVariable Long hookId) {
        hookService.deleteHook(serverId, hookId);
        return ResponseEntity.ok(ApiResponse.successMsg("Hook 已删除"));
    }
}