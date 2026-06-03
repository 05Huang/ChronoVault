package com.chronovault.controller;

import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.stash.CreateStashRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.SnapshotStashService;
import com.chronovault.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servers/{serverId}/stash")
@RequiredArgsConstructor
public class ServerStashController {

    private final SnapshotStashService stashService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotDTO>> createStash(
            Authentication auth,
            @PathVariable Long serverId,
            @RequestBody(required = false) CreateStashRequest body) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        String note = body != null ? body.note() : null;
        return ResponseEntity.ok(ApiResponse.success(stashService.createStash(serverId, note, userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SnapshotDTO>>> listStashes(@PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(stashService.listStashes(serverId)));
    }

    @PostMapping("/pop")
    public ResponseEntity<ApiResponse<String>> popStash(
            Authentication auth,
            @PathVariable Long serverId) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        String result = stashService.popStash(serverId, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{stashId}")
    public ResponseEntity<ApiResponse<Void>> discardStash(
            @PathVariable Long serverId,
            @PathVariable Long stashId) {
        stashService.discardStash(serverId, stashId);
        return ResponseEntity.ok(ApiResponse.successMsg("暂存快照已丢弃"));
    }
}
