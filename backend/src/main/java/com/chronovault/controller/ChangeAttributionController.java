package com.chronovault.controller;

import com.chronovault.dto.blame.ChangeAttribution;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.ChangeAttributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChangeAttributionController {

    private final ChangeAttributionService attributionService;

    @GetMapping("/servers/{serverId}/blame")
    public ResponseEntity<ApiResponse<List<ChangeAttribution>>> getServerBlame(
            @PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(attributionService.getServerBlame(serverId)));
    }

    @GetMapping("/snapshots/{snapshotId}/blame")
    public ResponseEntity<ApiResponse<List<ChangeAttribution>>> getSnapshotBlame(
            @PathVariable Long snapshotId) {
        return ResponseEntity.ok(ApiResponse.success(attributionService.getSnapshotBlame(snapshotId)));
    }
}