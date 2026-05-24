package com.chronovault.controller;

import com.chronovault.ai.AiAnalysisService;
import com.chronovault.dto.server.*;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.AgentInstallService;
import com.chronovault.service.ServerHealthMonitor;
import com.chronovault.service.ServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;
    private final ServerHealthMonitor healthMonitor;
    private final AiAnalysisService aiAnalysisService;
    private final AgentInstallService agentInstallService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServerDTO>>> getServers(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getServers(auth.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServerDTO>> getServer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getServer(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServerDTO>> createServer(Authentication auth, @Valid @RequestBody CreateServerRequest request) {
        ServerDTO server = serverService.createServer(auth.getName(), request.name(), request.ip(), request.os());
        return ResponseEntity.ok(ApiResponse.success(server));
    }

    @GetMapping("/{id}/containers")
    public ResponseEntity<ApiResponse<List<ContainerDTO>>> getContainers(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getContainers(id)));
    }

    @GetMapping("/{id}/volumes")
    public ResponseEntity<ApiResponse<List<VolumeDTO>>> getVolumes(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getVolumes(id)));
    }

    @PostMapping("/{id}/volumes")
    public ResponseEntity<ApiResponse<VolumeDTO>> addVolume(@PathVariable Long id, @Valid @RequestBody AddVolumeRequest request) {
        VolumeDTO volume = serverService.addVolume(id, request.containerPath(), request.hostPath());
        return ResponseEntity.ok(ApiResponse.success(volume));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<List<LogEntryDTO>>> getLogs(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getLogs(id, 100)));
    }

    @DeleteMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<Void>> clearLogs(@PathVariable Long id) {
        serverService.clearLogs(id);
        return ResponseEntity.ok(ApiResponse.successMsg("日志已清空"));
    }

    @PostMapping("/{id}/connect")
    public ResponseEntity<ApiResponse<Map<String, String>>> connect(@PathVariable Long id) {
        ServerDTO server = serverService.getServer(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "host", server.ip(),
                "port", String.valueOf(server.sshPort() != null ? server.sshPort() : 22),
                "username", server.sshUsername() != null ? server.sshUsername() : "root",
                "authMethod", server.sshAuthMethod() != null ? server.sshAuthMethod() : "KEY"
        )));
    }

    @PutMapping("/{id}/ssh")
    public ResponseEntity<ApiResponse<ServerDTO>> updateSshConfig(@PathVariable Long id, @Valid @RequestBody UpdateSshConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                serverService.updateSshConfig(id, request.port(), request.username(), request.authMethod(), request.credential())));
    }

    @PostMapping("/{id}/test-connection")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnection(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.testConnection(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteServer(@PathVariable Long id) {
        serverService.deleteServer(id);
        return ResponseEntity.ok(ApiResponse.successMsg("服务器已删除"));
    }

    // --- Health Monitoring ---

    @GetMapping("/{id}/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(healthMonitor.getServerHealth(id)));
    }

    @PostMapping("/{id}/health/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshHealth(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(healthMonitor.forceRefresh(id)));
    }

    // --- Docker Lifecycle ---

    @PostMapping("/{id}/containers/{cid}/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startContainer(@PathVariable Long id, @PathVariable String cid) {
        return ResponseEntity.ok(ApiResponse.success(serverService.startContainer(id, cid)));
    }

    @PostMapping("/{id}/containers/{cid}/stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopContainer(@PathVariable Long id, @PathVariable String cid) {
        return ResponseEntity.ok(ApiResponse.success(serverService.stopContainer(id, cid)));
    }

    @PostMapping("/{id}/containers/{cid}/restart")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restartContainer(@PathVariable Long id, @PathVariable String cid) {
        return ResponseEntity.ok(ApiResponse.success(serverService.restartContainerAction(id, cid)));
    }

    @DeleteMapping("/{id}/containers/{cid}")
    public ResponseEntity<ApiResponse<Void>> removeContainer(@PathVariable Long id, @PathVariable String cid,
                                                              @RequestParam(defaultValue = "false") boolean force) {
        serverService.removeContainer(id, cid, force);
        return ResponseEntity.ok(ApiResponse.successMsg("容器已删除"));
    }

    @PostMapping("/{id}/containers/create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createContainer(@PathVariable Long id,
                                                                             @Valid @RequestBody CreateContainerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(serverService.createContainer(id, request)));
    }

    // --- Docker Image Management ---

    @GetMapping("/{id}/images")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getImages(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getImages(id)));
    }

    @PostMapping("/{id}/images/pull")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pullImage(@PathVariable Long id,
                                                                       @RequestBody Map<String, String> body) {
        String image = body.get("image");
        if (image == null || image.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.success(Map.of("success", false, "message", "镜像名称不能为空")));
        }
        return ResponseEntity.ok(ApiResponse.success(serverService.pullImage(id, image)));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> removeImage(@PathVariable Long id, @PathVariable String imageId,
                                                          @RequestParam(defaultValue = "false") boolean force) {
        serverService.removeImage(id, imageId, force);
        return ResponseEntity.ok(ApiResponse.successMsg("镜像已删除"));
    }

    // --- Docker Network ---

    @GetMapping("/{id}/networks")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getNetworks(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getNetworks(id)));
    }

    @GetMapping("/{id}/topology")
    public ResponseEntity<ApiResponse<List<String[]>>> getTopology(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getTopologyEdges(id)));
    }

    @PostMapping("/{id}/scan-environment")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scanEnvironment(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.scanEnvironment(id)));
    }

    @PostMapping("/{id}/ai-analyze")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aiAnalyze(@PathVariable Long id) {
        Map<String, Object> scanResult = serverService.scanEnvironment(id);
        if (!Boolean.TRUE.equals(scanResult.get("success"))) {
            return ResponseEntity.ok(ApiResponse.success(scanResult));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> scanData = (Map<String, Object>) scanResult.get("data");
        String analysis = aiAnalysisService.analyzeEnvironment(id, scanData);
        return ResponseEntity.ok(ApiResponse.success(Map.of("analysis", analysis)));
    }

    @PostMapping("/{id}/install-agent")
    public ResponseEntity<ApiResponse<Map<String, Object>>> installAgent(
            @PathVariable Long id, Authentication auth,
            jakarta.servlet.http.HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        Map<String, Object> result = agentInstallService.installAgent(id, auth.getName(), requestUrl);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/batch-scan")
    public ResponseEntity<ApiResponse<String>> batchScan(@RequestBody List<Long> ids) {
        int scanned = serverService.batchScan(ids);
        return ResponseEntity.ok(ApiResponse.success("已触发 " + scanned + " 台服务器扫描"));
    }
}
