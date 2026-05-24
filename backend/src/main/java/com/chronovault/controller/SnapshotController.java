package com.chronovault.controller;

import com.chronovault.dto.snapshot.CreateSnapshotRequest;
import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotDiffDTO;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.SnapshotService;
import com.chronovault.service.SnapshotTagService;
import com.chronovault.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;
    private final SnapshotTagService tagService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getSnapshots(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            Page<SnapshotDTO> result = snapshotService.getSnapshotsPaged(page, size);
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshots()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SnapshotDTO>> getSnapshot(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshot(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotDTO>> createSnapshot(Authentication auth, @Valid @RequestBody CreateSnapshotRequest request) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        return ResponseEntity.ok(ApiResponse.success(snapshotService.createSnapshot(request, userId)));
    }

    @GetMapping("/{id}/diff")
    public ResponseEntity<ApiResponse<List<SnapshotDiffDTO>>> getDiff(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshotDiff(id)));
    }

    @PostMapping("/{id}/rollback")
    public ResponseEntity<ApiResponse<Void>> rollback(Authentication auth, @PathVariable Long id) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        snapshotService.rollback(id, userId);
        return ResponseEntity.ok(ApiResponse.successMsg("回滚成功"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSnapshot(@PathVariable Long id) {
        snapshotService.deleteSnapshot(id);
        return ResponseEntity.ok(ApiResponse.successMsg("快照已删除"));
    }

    @GetMapping("/tags/all")
    public ResponseEntity<ApiResponse<List<SnapshotTagDTO>>> getAllTags() {
        return ResponseEntity.ok(ApiResponse.success(tagService.getAllTags()));
    }

    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<String>> cleanupLocalRepo() {
        String result = snapshotService.cleanupLocalRepo();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/batch-delete")
    public ResponseEntity<ApiResponse<String>> batchDelete(@RequestBody List<Long> ids) {
        int deleted = snapshotService.batchDelete(ids);
        return ResponseEntity.ok(ApiResponse.success("已删除 " + deleted + " 个快照"));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportSnapshots(@RequestParam(defaultValue = "csv") String format) {
        List<SnapshotDTO> snapshots = snapshotService.getSnapshots();

        if ("json".equalsIgnoreCase(format)) {
            // Build JSON manually to avoid dependency
            StringBuilder json = new StringBuilder("[\n");
            for (int i = 0; i < snapshots.size(); i++) {
                SnapshotDTO s = snapshots.get(i);
                json.append(String.format(
                    "  {\"id\":%d,\"name\":\"%s\",\"status\":\"%s\",\"serverName\":\"%s\",\"createdAt\":\"%s\",\"sizeBytes\":%d}",
                    s.id(), escapeJson(s.name()), s.status(), escapeJson(s.serverName()), s.createdAt(), s.sizeBytes()));
                if (i < snapshots.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("]");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=snapshots.json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.toString());
        }

        // Default: CSV
        StringBuilder csv = new StringBuilder("ID,名称,状态,服务器,创建时间,大小\n");
        for (SnapshotDTO s : snapshots) {
            csv.append(String.format("%d,%s,%s,%s,%s,%d\n",
                    s.id(), csvEscape(s.name()), s.status(), csvEscape(s.serverName()), s.createdAt(), s.sizeBytes()));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=snapshots.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
