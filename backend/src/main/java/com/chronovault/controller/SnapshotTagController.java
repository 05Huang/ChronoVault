package com.chronovault.controller;

import com.chronovault.dto.snapshot.CreateTagRequest;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.SnapshotTagService;
import com.chronovault.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snapshots/{snapshotId}/tags")
@RequiredArgsConstructor
public class SnapshotTagController {

    private final SnapshotTagService tagService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SnapshotTagDTO>>> getTags(@PathVariable Long snapshotId) {
        return ResponseEntity.ok(ApiResponse.success(tagService.getTagsBySnapshot(snapshotId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotTagDTO>> addTag(
            @PathVariable Long snapshotId,
            Authentication auth,
            @Valid @RequestBody CreateTagRequest request) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        return ResponseEntity.ok(ApiResponse.success(tagService.addTag(snapshotId, request, userId)));
    }

    @DeleteMapping("/{tagName}")
    public ResponseEntity<ApiResponse<Void>> removeTag(
            @PathVariable Long snapshotId,
            @PathVariable String tagName) {
        tagService.removeTag(snapshotId, tagName);
        return ResponseEntity.ok(ApiResponse.successMsg("标签已删除"));
    }
}
