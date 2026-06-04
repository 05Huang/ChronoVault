package com.chronovault.controller;

import com.chronovault.dto.terminal.TerminalExecRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.TerminalService;
import jakarta.validation.Valid;
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
        String sessionId = terminalService.createSession(serverId, SecurityUtils.getCurrentUsername(auth));
        return ResponseEntity.ok(ApiResponse.success(Map.of("sessionId", sessionId)));
    }

    /**
     * Execute a command in a terminal session.
     */
    @PostMapping("/sessions/{sessionId}/exec")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeCommand(
            @PathVariable String sessionId,
            @Valid @RequestBody TerminalExecRequest body) {
        return ResponseEntity.ok(ApiResponse.success(terminalService.executeCommand(sessionId, body.command())));
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
