package com.chronovault.controller;

import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.TerminalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/terminal")
@RequiredArgsConstructor
public class TerminalController {

    private final TerminalService terminalService;

    /**
     * Create a new terminal session for a server.
     */
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSession(
            Authentication auth,
            @RequestParam Long serverId) {
        String sessionId = terminalService.createSession(serverId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(Map.of("sessionId", sessionId)));
    }

    /**
     * Execute a command in a terminal session.
     */
    @PostMapping("/sessions/{sessionId}/exec")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeCommand(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        String command = body.get("command");
        if (command == null || command.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.success(Map.of("error", "命令不能为空")));
        }
        return ResponseEntity.ok(ApiResponse.success(terminalService.executeCommand(sessionId, command)));
    }

    /**
     * Close a terminal session.
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> closeSession(@PathVariable String sessionId) {
        terminalService.closeSession(sessionId);
        return ResponseEntity.ok(ApiResponse.successMsg("会话已关闭"));
    }

    /**
     * Get terminal status.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "activeSessions", terminalService.getActiveSessionCount()
        )));
    }
}
