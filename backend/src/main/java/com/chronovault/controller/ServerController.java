package com.chronovault.controller;

import com.chronovault.dto.server.*;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.ServerService;
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServerDTO>>> getServers(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getServers(auth.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServerDTO>> getServer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getServer(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServerDTO>> createServer(Authentication auth, @RequestBody Map<String, String> body) {
        ServerDTO server = serverService.createServer(auth.getName(),
                body.get("name"), body.get("ip"), body.get("os"));
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
    public ResponseEntity<ApiResponse<VolumeDTO>> addVolume(@PathVariable Long id, @RequestBody Map<String, String> body) {
        VolumeDTO volume = serverService.addVolume(id, body.get("containerPath"), body.get("hostPath"));
        return ResponseEntity.ok(ApiResponse.success(volume));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<List<LogEntryDTO>>> getLogs(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getLogs(id, 100)));
    }

    @DeleteMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<Void>> clearLogs(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("日志已清空", null));
    }

    @PostMapping("/{id}/connect")
    public ResponseEntity<ApiResponse<Map<String, String>>> connect(@PathVariable Long id) {
        ServerDTO server = serverService.getServer(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "host", server.ip(),
                "port", "22",
                "username", "root",
                "authMethod", "key"
        )));
    }

    @PutMapping("/{id}/ssh")
    public ResponseEntity<ApiResponse<ServerDTO>> updateSshConfig(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer port = body.containsKey("port") ? Integer.parseInt(body.get("port").toString()) : null;
        String username = (String) body.get("username");
        String authMethod = (String) body.get("authMethod");
        String credential = (String) body.get("credential");
        return ResponseEntity.ok(ApiResponse.success(serverService.updateSshConfig(id, port, username, authMethod, credential)));
    }

    @PostMapping("/{id}/test-connection")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnection(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.testConnection(id)));
    }
}
