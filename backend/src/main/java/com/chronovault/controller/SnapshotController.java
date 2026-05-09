package com.chronovault.controller;

import com.chronovault.dto.snapshot.CreateSnapshotRequest;
import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotDiffDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.SnapshotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SnapshotDTO>>> getSnapshots() {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshots()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SnapshotDTO>> getSnapshot(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshot(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotDTO>> createSnapshot(Authentication auth, @Valid @RequestBody CreateSnapshotRequest request) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.createSnapshot(request, 1L)));
    }

    @GetMapping("/{id}/diff")
    public ResponseEntity<ApiResponse<List<SnapshotDiffDTO>>> getDiff(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshotDiff(id)));
    }

    @PostMapping("/{id}/rollback")
    public ResponseEntity<ApiResponse<Void>> rollback(@PathVariable Long id) {
        snapshotService.rollback(id, 1L);
        return ResponseEntity.ok(ApiResponse.success("回滚成功", null));
    }
}
