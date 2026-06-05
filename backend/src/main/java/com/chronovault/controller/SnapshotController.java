package com.chronovault.controller;

import com.chronovault.audit.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.chronovault.dto.snapshot.BisectMarkRequest;
import com.chronovault.dto.snapshot.BisectSessionDTO;
import com.chronovault.dto.snapshot.BisectStartRequest;
import com.chronovault.dto.snapshot.BatchDeleteRequest;
import com.chronovault.dto.snapshot.BatchTagRequest;
import com.chronovault.dto.snapshot.CherryPickRequest;
import com.chronovault.dto.snapshot.CreateSnapshotRequest;
import com.chronovault.dto.snapshot.ReplicateSnapshotRequest;
import com.chronovault.dto.snapshot.SelectiveRestoreRequest;
import com.chronovault.dto.snapshot.SelectiveRollbackRequest;
import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotDiffDTO;
import com.chronovault.dto.snapshot.SnapshotFileEntry;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.dto.snapshot.SnapshotVerifyResult;
import com.chronovault.dto.snapshot.StartBatchRequest;
import com.chronovault.dto.snapshot.ContainerStateDTO;
import com.chronovault.repository.ContainerStateRepository;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.SnapshotBisectService;
import com.chronovault.service.SnapshotService;
import com.chronovault.service.SnapshotTagService;
import com.chronovault.service.BatchSnapshotService;
import com.chronovault.service.StorageReplicationService;
import com.chronovault.service.UserService;
import com.chronovault.task.AsyncTaskManager;
import com.chronovault.task.TaskType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.chronovault.config.ApiVersion;

@Slf4j
@RestController
@RequestMapping(ApiVersion.V1 + "/snapshots")
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
    private final AsyncTaskManager taskManager;

    /** In-memory store for async export results, keyed by task ID. TTL is managed by cleanup. */
    private final ConcurrentHashMap<Long, ExportResult> exportResults = new ConcurrentHashMap<>();

    private record ExportResult(String filename, String contentType, byte[] content) {}

    @GetMapping
    @Operation(summary = "获取快照列表（分页）", description = "返回分页快照列表，支持按标签过滤。默认 page=0, size=20, sort=createdAt, direction=desc")
    public ResponseEntity<?> getSnapshots(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String tagName,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        if (tagName != null && !tagName.isBlank()) {
            Page<SnapshotDTO> result = snapshotService.getSnapshotsByTagPaged(tagName, page, size, sort, direction);
            return ResponseEntity.ok(ApiResponse.successPage(
                    result.getContent(), page, size, result.getTotalElements()));
        }
        Page<SnapshotDTO> result = snapshotService.getSnapshotsPaged(page, size, sort, direction);
        return ResponseEntity.ok(ApiResponse.successPage(
                result.getContent(), page, size, result.getTotalElements()));
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有快照（不分页）", description = "返回所有快照列表，仅适用于数据量较小的场景")
    public ResponseEntity<ApiResponse<List<SnapshotDTO>>> getAllSnapshots() {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshots()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SnapshotDTO>> getSnapshot(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshot(id)));
    }

    @Auditable(action = "创建快照", changeType = "SNAPSHOT_CREATED")
    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotDTO>> createSnapshot(Authentication auth, @Valid @RequestBody CreateSnapshotRequest request) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        SnapshotDTO snapshot = snapshotService.createSnapshot(request, userId);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "snapshots/" + snapshot.id()))
                .body(ApiResponse.success(snapshot));
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

    // ===== State.json endpoints (P0-4) =====

    @GetMapping("/{id}/state")
    @Operation(summary = "获取快照的 state.json", description = "返回 Agent 采集的系统状态 JSON（包、服务、端口、Docker、配置）")
    public ResponseEntity<ApiResponse<String>> getStateSnapshot(@PathVariable Long id) {
        String stateJson = snapshotService.getStateSnapshot(id);
        if (stateJson == null) {
            return ResponseEntity.ok(ApiResponse.success("此快照没有系统状态数据", null));
        }
        return ResponseEntity.ok(ApiResponse.success(stateJson));
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "获取快照变更摘要", description = "返回该快照相对于上一个快照的变更摘要（用于时间线视图）")
    public ResponseEntity<ApiResponse<String>> getChangeSummary(@PathVariable Long id) {
        String summary = snapshotService.getChangeSummary(id);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/state-diff")
    @Operation(summary = "对比两个快照的系统状态", description = "计算两个快照的 state.json 之间的结构化差异")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStateDiff(
            @RequestParam Long from,
            @RequestParam Long to) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.computeStateDiff(from, to)));
    }

    @GetMapping("/timeline")
    @Operation(summary = "获取快照时间线", description = "返回指定服务器的快照时间线（含变更摘要）。默认 sort=createdAt, direction=desc")
    public ResponseEntity<ApiResponse<List<SnapshotDTO>>> getTimeline(
            @RequestParam Long serverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        List<SnapshotDTO> snapshots = snapshotService.getSnapshotsForTimeline(serverId, page, size, sort, direction);
        return ResponseEntity.ok(ApiResponse.success(snapshots));
    }

    @Auditable(action = "回滚快照", changeType = "SNAPSHOT_RESTORED")
    @PostMapping("/{id}/rollback")
    public ResponseEntity<ApiResponse<Void>> rollback(Authentication auth, @PathVariable Long id) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        snapshotService.rollback(id, userId);
        return ResponseEntity.ok(ApiResponse.successMsg("回滚成功"));
    }

    @GetMapping("/{id}/rollback/preview")
    @Operation(summary = "回滚预演", description = "预览回滚操作将产生的影响，不执行实际回滚")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rollbackPreview(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.rollbackPreview(id)));
    }

    @Auditable(action = "选择性回滚", changeType = "SNAPSHOT_RESTORED", resourceType = "SNAPSHOT", resourceId = "#id")
    @PostMapping("/{id}/rollback/selective")
    @Operation(summary = "选择性回滚", description = "只回滚指定的配置文件或包版本")
    public ResponseEntity<ApiResponse<String>> selectiveRollback(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody SelectiveRollbackRequest request) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        String result = snapshotService.selectiveRollback(id, request.items(), userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Auditable(action = "撤销快照", changeType = "SNAPSHOT_REVERTED")
    @PostMapping("/{id}/revert")
    public ResponseEntity<ApiResponse<String>> revert(Authentication auth, @PathVariable Long id) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        String result = snapshotService.revert(id, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/cherry-pick")
    public ResponseEntity<ApiResponse<String>> cherryPick(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody CherryPickRequest request) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
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
            @Valid @RequestBody ReplicateSnapshotRequest body) {
        replicationService.replicateSnapshot(id, body.targetStorageId());
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
            @Valid @RequestBody BatchTagRequest body) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        int count = tagService.bulkTag(body.snapshotIds(), body.tagName(), body.color(), userId);
        return ResponseEntity.ok(ApiResponse.success("已为 " + count + " 个快照添加标签"));
    }

    // === Bisect endpoints ===

    @PostMapping("/bisect/start")
    public ResponseEntity<ApiResponse<BisectSessionDTO>> startBisect(
            @Valid @RequestBody BisectStartRequest request) {
        BisectSessionDTO session = bisectService.start(request);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "snapshots/bisect/" + session.sessionId()))
                .body(ApiResponse.success(session));
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
    public ResponseEntity<ApiResponse<String>> batchDelete(@Valid @RequestBody BatchDeleteRequest request) {
        int deleted = snapshotService.batchDelete(request.ids());
        return ResponseEntity.ok(ApiResponse.success("已删除 " + deleted + " 个快照"));
    }

    // === Batch snapshot endpoints ===

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<String>> startBatch(
            Authentication auth,
            @Valid @RequestBody StartBatchRequest body) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        String batchId = batchService.startBatch(body.serverIds(), body.storageTargetId(), body.name(), userId);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "snapshots/batch/" + batchId))
                .body(ApiResponse.success(batchId));
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<ApiResponse<BatchSnapshotService.BatchStatus>> getBatchStatus(
            @PathVariable String batchId) {
        return ResponseEntity.ok(ApiResponse.success(batchService.getBatchStatus(batchId)));
    }

    @GetMapping("/export")
    @Operation(summary = "导出快照数据", description = "异步导出快照数据，返回任务 ID。客户端应轮询任务状态，完成后通过 /export/{taskId}/download 下载文件")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportSnapshots(
            Authentication auth,
            @RequestParam(defaultValue = "csv") String format) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        String fmt = format.toLowerCase();
        if (!fmt.equals("csv") && !fmt.equals("json") && !fmt.equals("yaml") && !fmt.equals("yml")) {
            throw new com.chronovault.exception.BadRequestException("不支持的导出格式: " + format + "，支持 csv/json/yaml");
        }

        com.chronovault.entity.AsyncTask task = taskManager.submit(
                TaskType.EXPORT, null, userId,
                "导出快照数据 (" + fmt.toUpperCase() + ")",
                t -> {
                    try {
                        taskManager.updateProgress(t.getId(), 10, "正在查询快照数据...");
                        List<SnapshotDTO> snapshots = snapshotService.getSnapshots();
                        taskManager.updateProgress(t.getId(), 50, "正在生成 " + fmt.toUpperCase() + " 文件...");

                        String filename = "snapshots_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "." + fmt;
                        byte[] content;

                        if ("yaml".equals(fmt) || "yml".equals(fmt)) {
                            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
                                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                            String yaml = yamlMapper.writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(Map.of("snapshots", snapshots));
                            content = yaml.getBytes(StandardCharsets.UTF_8);
                        } else if ("json".equals(fmt)) {
                            ObjectMapper jsonMapper = new ObjectMapper()
                                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                            String json = jsonMapper.writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(snapshots);
                            content = json.getBytes(StandardCharsets.UTF_8);
                        } else {
                            // CSV
                            StringBuilder csv = new StringBuilder("ID,名称,状态,服务器,创建时间,大小\n");
                            for (SnapshotDTO s : snapshots) {
                                csv.append(String.format("%d,%s,%s,%s,%s,%d\n",
                                        s.id(), csvEscape(s.name()), s.status(),
                                        csvEscape(s.serverName()), s.createdAt(), s.sizeBytes()));
                            }
                            content = csv.toString().getBytes(StandardCharsets.UTF_8);
                        }

                        String contentType = switch (fmt) {
                            case "yaml", "yml" -> "text/yaml";
                            case "json" -> "application/json";
                            default -> "text/csv";
                        };

                        exportResults.put(t.getId(), new ExportResult(filename, contentType, content));
                        taskManager.updateProgress(t.getId(), 100, "导出完成，文件已就绪");
                        log.info("[EXPORT] [task={}] Export completed: {} ({} bytes)", t.getId(), filename, content.length);
                    } catch (Exception e) {
                        throw new RuntimeException("导出失败: " + e.getMessage(), e);
                    }
                });

        return ResponseEntity.accepted()
                .body(ApiResponse.success(Map.of(
                        "taskId", task.getId(),
                        "message", "导出任务已提交，请轮询任务状态，完成后通过 /api/snapshots/export/" + task.getId() + "/download 下载")));
    }

    @GetMapping("/export/{taskId}/download")
    @Operation(summary = "下载导出文件", description = "快照导出任务完成后，通过此端点下载导出文件")
    public ResponseEntity<byte[]> downloadExport(@PathVariable Long taskId) {
        ExportResult result = exportResults.get(taskId);
        if (result == null) {
            // Check if task exists and is still running
            com.chronovault.entity.AsyncTask task = taskManager.getStatus(taskId);
            if (task == null) {
                throw new com.chronovault.exception.ResourceNotFoundException("导出任务不存在: " + taskId);
            }
            if (task.getStatus() == com.chronovault.entity.AsyncTask.TaskStatus.RUNNING
                    || task.getStatus() == com.chronovault.entity.AsyncTask.TaskStatus.PENDING) {
                throw new com.chronovault.exception.BadRequestException("导出任务尚未完成，请稍后再试");
            }
            throw new com.chronovault.exception.BadRequestException("导出结果已过期或下载失败");
        }

        // Clean up after download to free memory
        exportResults.remove(taskId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.content().length)
                .body(result.content());
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        // Prevent CSV injection: prefix formula-triggering characters
        if (!s.isEmpty() && (s.charAt(0) == '=' || s.charAt(0) == '+' || s.charAt(0) == '-' || s.charAt(0) == '@')) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @GetMapping("/{id}/impact")
    @Operation(summary = "快照影响分析", description = "分析指定快照影响了哪些文件、服务和配置，返回变更摘要")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSnapshotImpact(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshotImpact(id)));
    }
}
