package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalService {

    private final ServerRepository serverRepository;
    private final SshConnectionManager sshManager;

    // Blocked command patterns (case-insensitive prefix match)
    private static final List<String> BLOCKED_COMMANDS = List.of(
        "rm -rf /", "rm -rf /*", "mkfs", "dd if=", "dd of=",
        "> /dev/sd", "chmod -R 777 /", "chown -R",
        ":(){ :|:& };:", "fork bomb", "shutdown", "reboot", "halt", "poweroff",
        "init 0", "init 6", "systemctl stop ssh", "systemctl disable ssh",
        "iptables -F", "iptables --flush"
    );

    // Active terminal sessions
    private final ConcurrentHashMap<String, TerminalSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Create a new interactive terminal session.
     */
    public String createSession(Long serverId, String email) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        try {
            SshConnection sshConn = sshManager.getConnection(server);
            // We need the raw ClientSession to open a shell channel
            // Get it via reflection or by extending SshConnection
            // For now, use the executeCommand approach with a persistent shell

            String sessionId = serverId + "-" + System.currentTimeMillis();
            TerminalSession session = new TerminalSession(sessionId, server, email);
            activeSessions.put(sessionId, session);

            log.info("Terminal session {} created for {}@{} by {}", sessionId, server.getSshUsername(), server.getIp(), email);
            return sessionId;
        } catch (Exception e) {
            throw new RuntimeException("创建终端会话失败: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a command in the terminal session and return output.
     */
    public Map<String, Object> executeCommand(String sessionId, String command) {
        TerminalSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Map.of("error", "会话不存在或已过期");
        }

        // Check for dangerous commands
        String cmdLower = command.toLowerCase().trim();
        for (String blocked : BLOCKED_COMMANDS) {
            if (cmdLower.startsWith(blocked) || cmdLower.contains(blocked)) {
                log.warn("Blocked dangerous command from session {}: {}", sessionId, command);
                return Map.of("error", "该命令已被安全策略阻止: " + blocked,
                        "exitCode", -1, "success", false);
            }
        }

        try {
            SshConnection conn = sshManager.getConnection(session.server);
            SshConnection.CommandResult result = conn.executeCommand(command);
            session.lastActivity = System.currentTimeMillis();
            return Map.of(
                    "exitCode", result.exitCode(),
                    "stdout", result.stdout(),
                    "stderr", result.stderr(),
                    "success", result.isSuccess()
            );
        } catch (Exception e) {
            return Map.of("error", "命令执行失败: " + e.getMessage());
        }
    }

    /**
     * Close a terminal session.
     */
    public void closeSession(String sessionId) {
        TerminalSession session = activeSessions.remove(sessionId);
        if (session != null) {
            log.info("Terminal session {} closed", sessionId);
        }
    }

    /**
     * Get active session count.
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    private static class TerminalSession {
        final String id;
        final Server server;
        final String email;
        long lastActivity;

        TerminalSession(String id, Server server, String email) {
            this.id = id;
            this.server = server;
            this.email = email;
            this.lastActivity = System.currentTimeMillis();
        }
    }
}
