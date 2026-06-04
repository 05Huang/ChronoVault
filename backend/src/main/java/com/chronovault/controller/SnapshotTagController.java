package com.chronovault.controller;

import com.chronovault.dto.snapshot.CreateTagRequest;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.SnapshotTagService;
import com.chronovault.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/snapshots/{snapshotId}/tags")
@RequiredArgsConstructor
public class SnapshotTagController {

    private final SnapshotTagService tagService;
    private final UserService userService;

    @Operation(summary = "获取 Tags")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SnapshotTagDTO>>> getTags(@PathVariable Long snapshotId) {
        return ResponseEntity.ok(ApiResponse.success(tagService.getTagsBySnapshot(snapshotId)));
    }

    @Operation(summary = "操作 Tag")
    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotTagDTO>> addTag(
            @PathVariable Long snapshotId,
            Authentication auth,
            @Valid @RequestBody CreateTagRequest request) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        SnapshotTagDTO tag = tagService.addTag(snapshotId, request, userId);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "snapshots/" + snapshotId + "/tags/" + tag.name()))
                .body(ApiResponse.success(tag));
    }

    @Operation(summary = "删除 Tag")
    @DeleteMapping("/{tagName}")
    public ResponseEntity<ApiResponse<Void>> removeTag(
            @PathVariable Long snapshotId,
            @PathVariable String tagName) {
        tagService.removeTag(snapshotId, tagName);
        return ResponseEntity.ok(ApiResponse.successMsg("标签已删除"));
    }
}
