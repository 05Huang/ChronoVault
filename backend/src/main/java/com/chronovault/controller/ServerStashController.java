package com.chronovault.controller;

import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.stash.CreateStashRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.SnapshotStashService;
import com.chronovault.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/servers/{serverId}/stash")
@RequiredArgsConstructor
public class ServerStashController {

    private final SnapshotStashService stashService;
    private final UserService userService;

    @Operation(summary = "操作 Stash")
    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotDTO>> createStash(
            Authentication auth,
            @PathVariable Long serverId,
            @RequestBody(required = false) CreateStashRequest body) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        String note = body != null ? body.note() : null;
        SnapshotDTO stash = stashService.createStash(serverId, note, userId);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "servers/" + serverId + "/stash/" + stash.id()))
                .body(ApiResponse.success(stash));
    }

    @Operation(summary = "获取 list Stashes")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SnapshotDTO>>> listStashes(@PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(stashService.listStashes(serverId)));
    }

    @Operation(summary = "操作 pop Stash")
    @PostMapping("/pop")
    public ResponseEntity<ApiResponse<String>> popStash(
            Authentication auth,
            @PathVariable Long serverId) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        String result = stashService.popStash(serverId, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "删除 Stash")
    @DeleteMapping("/{stashId}")
    public ResponseEntity<ApiResponse<Void>> discardStash(
            @PathVariable Long serverId,
            @PathVariable Long stashId) {
        stashService.discardStash(serverId, stashId);
        return ResponseEntity.ok(ApiResponse.successMsg("暂存快照已丢弃"));
    }
}
