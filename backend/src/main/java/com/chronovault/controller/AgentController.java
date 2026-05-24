package com.chronovault.controller;

import com.chronovault.agent.AgentCommunicationService;
import com.chronovault.entity.AsyncTask;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentCommunicationService agentService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody Map<String, String> body) {
        Long serverId = body.get("serverId") != null ? Long.valueOf(body.get("serverId")) : null;
        Map<String, Object> result = agentService.registerAgent(
                body.get("agentId"),
                body.get("name"),
                body.get("ip"),
                body.get("os"),
                body.get("agentVersion"),
                body.get("capabilities"),
                serverId
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(@RequestBody Map<String, Object> body) {
        String agentId = (String) body.get("agentId");
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) body.getOrDefault("metrics", Map.of());
        agentService.heartbeat(agentId, metrics);
        return ResponseEntity.ok(ApiResponse.successMsg("心跳已更新"));
    }

    @PostMapping("/tasks/pending")
    public ResponseEntity<ApiResponse<List<AsyncTask>>> getPendingTasks(@RequestBody Map<String, String> body) {
        List<AsyncTask> tasks = agentService.getPendingTasks(body.get("agentId"));
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @PostMapping("/tasks/{taskId}/progress")
    public ResponseEntity<ApiResponse<Void>> updateProgress(@PathVariable Long taskId,
                                                             @RequestBody Map<String, Object> body) {
        int progress = ((Number) body.get("progress")).intValue();
        String message = (String) body.get("message");
        agentService.updateTaskProgress(taskId, progress, message);
        return ResponseEntity.ok(ApiResponse.successMsg("进度已更新"));
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<ApiResponse<Void>> completeTask(@PathVariable Long taskId,
                                                           @RequestBody Map<String, String> body) {
        agentService.completeTask(taskId, body.get("result"));
        return ResponseEntity.ok(ApiResponse.successMsg("任务已完成"));
    }

    @PostMapping("/tasks/{taskId}/fail")
    public ResponseEntity<ApiResponse<Void>> failTask(@PathVariable Long taskId,
                                                       @RequestBody Map<String, String> body) {
        agentService.failTask(taskId, body.get("error"));
        return ResponseEntity.ok(ApiResponse.successMsg("任务已标记失败"));
    }

    @PostMapping("/containers/{serverId}")
    public ResponseEntity<ApiResponse<Void>> updateContainers(@PathVariable Long serverId,
                                                               @RequestBody List<Map<String, Object>> containers) {
        agentService.updateContainers(serverId, containers);
        return ResponseEntity.ok(ApiResponse.successMsg("容器信息已更新"));
    }
}
