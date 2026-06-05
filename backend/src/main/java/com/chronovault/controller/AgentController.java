package com.chronovault.controller;

import com.chronovault.agent.AgentCommunicationService;
import com.chronovault.dto.agent.*;
import com.chronovault.entity.AsyncTask;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentCommunicationService agentService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @Valid @RequestBody AgentRegisterRequest body) {
        Map<String, Object> result = agentService.registerAgent(
                body.agentId(),
                body.name(),
                body.ip(),
                body.os(),
                body.agentVersion(),
                body.capabilities(),
                body.serverId()
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "操作 heartbeat")
    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @Valid @RequestBody AgentHeartbeatRequest body) {
        agentService.heartbeat(body.agentId(), body.metrics() != null ? body.metrics() : Map.of());
        return ResponseEntity.ok(ApiResponse.successMsg("心跳已更新"));
    }

    @Operation(summary = "操作 get Pending Tasks")
    @PostMapping("/tasks/pending")
    public ResponseEntity<ApiResponse<List<AsyncTask>>> getPendingTasks(
            @Valid @RequestBody AgentPendingTasksRequest body) {
        List<AsyncTask> tasks = agentService.getPendingTasks(body.agentId());
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @Operation(summary = "操作 update Progress")
    @PostMapping("/tasks/{taskId}/progress")
    public ResponseEntity<ApiResponse<Void>> updateProgress(
            @PathVariable Long taskId,
            @Valid @RequestBody AgentTaskProgressRequest body) {
        agentService.updateTaskProgress(taskId, body.progress(), body.message());
        return ResponseEntity.ok(ApiResponse.successMsg("进度已更新"));
    }

    @Operation(summary = "操作 complete Task")
    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<ApiResponse<Void>> completeTask(
            @PathVariable Long taskId,
            @Valid @RequestBody AgentTaskResultRequest body) {
        agentService.completeTask(taskId, body.result());
        return ResponseEntity.ok(ApiResponse.successMsg("任务已完成"));
    }

    @Operation(summary = "操作 fail Task")
    @PostMapping("/tasks/{taskId}/fail")
    public ResponseEntity<ApiResponse<Void>> failTask(
            @PathVariable Long taskId,
            @Valid @RequestBody AgentTaskFailRequest body) {
        agentService.failTask(taskId, body.error());
        return ResponseEntity.ok(ApiResponse.successMsg("任务已标记失败"));
    }

    @Operation(summary = "操作 update Containers")
    @PostMapping("/containers/{serverId}")
    public ResponseEntity<ApiResponse<Void>> updateContainers(
            @PathVariable Long serverId,
            @RequestBody List<Map<String, Object>> containers) {
        agentService.updateContainers(serverId, containers);
        return ResponseEntity.ok(ApiResponse.successMsg("容器信息已更新"));
    }

    @Operation(summary = "获取最新 Agent 版本", description = "Agent 启动时检查是否有新版本可用")
    @GetMapping("/version")
    public ResponseEntity<ApiResponse<Map<String, String>>> getLatestVersion() {
        // Current latest version — in production this would come from a config or database
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "latestVersion", "0.1.0",
                "downloadUrl", "https://github.com/chronovault/chronovault/releases/latest",
                "releaseNotes", "Latest stable release"
        )));
    }
}
