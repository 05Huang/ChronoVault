package com.chronovault.controller;

import com.chronovault.audit.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.chronovault.dto.snapshot.BisectMarkRequest;
import com.chronovault.dto.snapshot.BisectSessionDTO;
import com.chronovault.dto.snapshot.BisectStartRequest;
import com.chronovault.dto.snapshot.CherryPickRequest;
import com.chronovault.dto.snapshot.CreateSnapshotRequest;
import com.chronovault.dto.snapshot.SelectiveRestoreRequest;
import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotDiffDTO;
import com.chronovault.dto.snapshot.SnapshotFileEntry;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.dto.snapshot.SnapshotVerifyResult;
import com.chronovault.dto.snapshot.ContainerStateDTO;
import com.chronovault.repository.ContainerStateRepository;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.SnapshotBisectService;
import com.chronovault.service.SnapshotService;
import com.chronovault.service.SnapshotTagService;
import com.chronovault.service.BatchSnapshotService;
import com.chronovault.service.StorageReplicationService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
@Tag(name = "Snapshots", description = "快照管理 — 创建、回滚、恢复、差异对比、二分查找")
public class SnapshotController {

    private final SnapshotService snapshotService;
    private final SnapshotTagService tagService;
    private final UserService userService;
    private final SnapshotBisectService bisectService;
    private final ContainerStateRepository containerStateRepository;
    private final StorageReplicationService replicationService;
    private final BatchSnapshotService batchService;

    @GetMapping
    public ResponseEntity<?> getSnapshots(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String tagName) {
        if (tagName != null && !tagName.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshotsByTag(tagName)));
        }
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

    @Auditable(action = "创建快照", changeType = "SNAPSHOT_CREATED")
    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotDTO>> createSnapshot(Authentication auth, @Valid @RequestBody CreateSnapshotRequest request) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        return ResponseEntity.ok(ApiResponse.success(snapshotService.createSnapshot(request, userId)));
    }

    @GetMapping("/{id}/diff")
    public ResponseEntity<ApiResponse<List<SnapshotDiffDTO>>> getDiff(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshotDiff(id)));
    }

    @GetMapping("/compare")
    public ResponseEntity<ApiResponse<SnapshotDiffDTO.DiffSummary>> compareSnapshots(
            @RequestParam Long from,
            @RequestParam Long to) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.compareSnapshots(from, to)));
    }

    @Auditable(action = "回滚快照", changeType = "SNAPSHOT_RESTORED")
    @PostMapping("/{id}/rollback")
    public ResponseEntity<ApiResponse<Void>> rollback(Authentication auth, @PathVariable Long id) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        snapshotService.rollback(id, userId);
        return ResponseEntity.ok(ApiResponse.successMsg("回滚成功"));
    }

    @Auditable(action = "撤销快照", changeType = "SNAPSHOT_REVERTED")
    @PostMapping("/{id}/revert")
    public ResponseEntity<ApiResponse<String>> revert(Authentication auth, @PathVariable Long id) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        String result = snapshotService.revert(id, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/cherry-pick")
    public ResponseEntity<ApiResponse<String>> cherryPick(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody CherryPickRequest request) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        String result = snapshotService.cherryPick(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}/files")
    public ResponseEntity<ApiResponse<List<SnapshotFileEntry>>> listFiles(
            @PathVariable Long id,
            @RequestParam(required = false) String path) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.listSnapshotFiles(id, path)));
    }

    @GetMapping("/{id}/files/download")
    public ResponseEntity<ApiResponse<String>> downloadFile(
            @PathVariable Long id,
            @RequestParam String path) {
        String content = snapshotService.getSnapshotFileContent(id, path);
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<SnapshotVerifyResult>> verifySnapshot(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.verifySnapshot(id)));
    }

    @GetMapping("/{id}/containers")
    public ResponseEntity<ApiResponse<List<ContainerStateDTO>>> getContainerStates(@PathVariable Long id) {
        List<ContainerStateDTO> states = containerStateRepository.findBySnapshotIdOrderByContainerNameAsc(id)
                .stream().map(ContainerStateDTO::from).toList();
        return ResponseEntity.ok(ApiResponse.success(states));
    }

    @GetMapping("/{id}/containers/compare")
    public ResponseEntity<ApiResponse<List<ContainerStateDTO>>> compareContainerStates(
            @PathVariable Long id,
            @RequestParam Long with) {
        List<ContainerStateDTO> current = containerStateRepository.findBySnapshotIdOrderByContainerNameAsc(id)
                .stream().map(ContainerStateDTO::from).toList();
        return ResponseEntity.ok(ApiResponse.success(current));
    }

    @PostMapping("/{id}/replicate")
    public ResponseEntity<ApiResponse<String>> replicateSnapshot(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Long> body) {
        Long targetStorageId = body.get("targetStorageId");
        if (targetStorageId == null) {
            throw new com.chronovault.exception.BadRequestException("请指定目标存储");
        }
        replicationService.replicateSnapshot(id, targetStorageId);
        return ResponseEntity.ok(ApiResponse.success("复制任务已提交，正在后台执行"));
    }

    @PostMapping("/{id}/restore-files")
    public ResponseEntity<ApiResponse<String>> restoreFiles(
            @PathVariable Long id,
            @Valid @RequestBody SelectiveRestoreRequest request) {
        String result = snapshotService.restoreFiles(id, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Auditable(action = "删除快照", changeType = "SNAPSHOT_DELETED")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSnapshot(@PathVariable Long id) {
        snapshotService.deleteSnapshot(id);
        return ResponseEntity.ok(ApiResponse.successMsg("快照已删除"));
    }

    @GetMapping("/tags/all")
    public ResponseEntity<ApiResponse<List<SnapshotTagDTO>>> getAllTags() {
        return ResponseEntity.ok(ApiResponse.success(tagService.getAllTags()));
    }

    @PostMapping("/batch-tag")
    public ResponseEntity<ApiResponse<String>> batchTag(
            Authentication auth,
            @RequestBody Map<String, Object> body) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        @SuppressWarnings("unchecked")
        List<Long> snapshotIds = (List<Long>) body.get("snapshotIds");
        String tagName = (String) body.get("tagName");
        String color = (String) body.get("color");
        int count = tagService.bulkTag(snapshotIds, tagName, color, userId);
        return ResponseEntity.ok(ApiResponse.success("已为 " + count + " 个快照添加标签"));
    }

    // === Bisect endpoints ===

    @PostMapping("/bisect/start")
    public ResponseEntity<ApiResponse<BisectSessionDTO>> startBisect(
            @Valid @RequestBody BisectStartRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bisectService.start(request)));
    }

    @PostMapping("/bisect/{sessionId}/mark")
    public ResponseEntity<ApiResponse<BisectSessionDTO>> markBisect(
            @PathVariable String sessionId,
            @Valid @RequestBody BisectMarkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bisectService.mark(sessionId, request)));
    }

    @GetMapping("/bisect/{sessionId}")
    public ResponseEntity<ApiResponse<BisectSessionDTO>> getBisectSession(
            @PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.success(bisectService.getSession(sessionId)));
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

    // === Batch snapshot endpoints ===

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<String>> startBatch(
            Authentication auth,
            @RequestBody Map<String, Object> body) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        @SuppressWarnings("unchecked")
        List<Long> serverIds = (List<Long>) body.get("serverIds");
        Long storageTargetId = body.get("storageTargetId") != null ? Long.valueOf(body.get("storageTargetId").toString()) : null;
        String name = body.get("name") != null ? body.get("name").toString() : null;
        String batchId = batchService.startBatch(serverIds, storageTargetId, name, userId);
        return ResponseEntity.ok(ApiResponse.success(batchId));
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<ApiResponse<BatchSnapshotService.BatchStatus>> getBatchStatus(
            @PathVariable String batchId) {
        return ResponseEntity.ok(ApiResponse.success(batchService.getBatchStatus(batchId)));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportSnapshots(@RequestParam(defaultValue = "csv") String format) {
        List<SnapshotDTO> snapshots = snapshotService.getSnapshots();

        if ("yaml".equalsIgnoreCase(format) || "yml".equalsIgnoreCase(format)) {
            StringBuilder yaml = new StringBuilder("snapshots:\n");
            for (SnapshotDTO s : snapshots) {
                yaml.append("  - id: ").append(s.id()).append("\n");
                yaml.append("    name: \"").append(escapeYaml(s.name())).append("\"\n");
                yaml.append("    status: \"").append(s.status()).append("\"\n");
                yaml.append("    server: \"").append(escapeYaml(s.serverName())).append("\"\n");
                yaml.append("    created_at: \"").append(s.createdAt()).append("\"\n");
                yaml.append("    size_bytes: ").append(s.sizeBytes()).append("\n");
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=snapshots.yaml")
                    .contentType(MediaType.parseMediaType("text/yaml"))
                    .body(yaml.toString());
        }

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

    private String escapeYaml(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }
}
