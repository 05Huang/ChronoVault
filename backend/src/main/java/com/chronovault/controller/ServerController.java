package com.chronovault.controller;

import com.chronovault.audit.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.chronovault.ai.AiAnalysisService;
import com.chronovault.dto.server.*;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.AgentInstallService;
import com.chronovault.service.AutoSnapshotService;
import com.chronovault.service.ServerCloneService;
import com.chronovault.service.ServerHealthMonitor;
import com.chronovault.service.ServerService;
import com.chronovault.service.UserService;
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
@Tag(name = "Servers", description = "服务器管理 — 添加、监控、克隆、漂移检测")
public class ServerController {

    private final ServerService serverService;
    private final ServerHealthMonitor healthMonitor;
    private final AiAnalysisService aiAnalysisService;
    private final AgentInstallService agentInstallService;
    private final ServerCloneService cloneService;
    private final AutoSnapshotService autoSnapshotService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "获取服务器列表", description = "返回当前用户可见的所有服务器")
    public ResponseEntity<ApiResponse<List<ServerDTO>>> getServers(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getServers(auth.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServerDTO>> getServer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getServer(id)));
    }

    @Auditable(action = "添加服务器", changeType = "SERVER_ADDED")
    @PostMapping
    public ResponseEntity<ApiResponse<ServerDTO>> createServer(Authentication auth, @Valid @RequestBody CreateServerRequest request) {
        ServerDTO server = serverService.createServer(auth.getName(), request.name(), request.ip(), request.os());
        return ResponseEntity.ok(ApiResponse.success(server));
    }

    @PostMapping("/clone")
    public ResponseEntity<ApiResponse<String>> cloneServer(
            Authentication auth,
            @Valid @RequestBody CloneServerRequest request) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        cloneService.cloneServer(request, userId);
        return ResponseEntity.ok(ApiResponse.success("克隆任务已提交，正在后台执行"));
    }

    @PutMapping("/{id}/auto-snapshot")
    public ResponseEntity<ApiResponse<Void>> toggleAutoSnapshot(
            @PathVariable Long id,
            @Valid @RequestBody ToggleAutoSnapshotRequest body) {
        autoSnapshotService.setAutoSnapshotEnabled(id, body.enabled());
        return ResponseEntity.ok(ApiResponse.successMsg("自动快照已" + (body.enabled() ? "开启" : "关闭")));
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
    @Operation(summary = "获取 SSH 连接信息", description = "返回服务器的 SSH 连接参数（不包含密钥）")
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

    @Auditable(action = "删除服务器", changeType = "SERVER_DELETED", resourceType = "SERVER", resourceId = "#id")
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> pullImage(
            @PathVariable Long id,
            @Valid @RequestBody PullImageRequest body) {
        return ResponseEntity.ok(ApiResponse.success(serverService.pullImage(id, body.image())));
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
    @Operation(summary = "安装 Agent", description = "通过 SSH 在目标服务器上安装 ChronoVault Agent")

    public ResponseEntity<ApiResponse<Map<String, Object>>> installAgent(
            @PathVariable Long id, Authentication auth,
            jakarta.servlet.http.HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        Map<String, Object> result = agentInstallService.installAgent(id, auth.getName(), requestUrl);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/batch-scan")
    public ResponseEntity<ApiResponse<String>> batchScan(@Valid @RequestBody BatchScanRequest request) {
        int scanned = serverService.batchScan(request.ids());
        return ResponseEntity.ok(ApiResponse.success("已触发 " + scanned + " 台服务器扫描"));
    }

    @PostMapping("/{id}/rotate-key")
    @Operation(summary = "轮换 SSH 密钥", description = "生成新的 Ed25519 密钥对，加密存储私钥，返回公钥供用户安装到目标服务器")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rotateKey(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.rotateKey(id)));
    }
}
