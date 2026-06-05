package com.chronovault.websocket;

import com.chronovault.agent.AgentCommunicationService;
import com.chronovault.entity.AgentInfo;
import com.chronovault.entity.AsyncTask;
import com.chronovault.repository.AgentInfoRepository;
import com.chronovault.repository.AsyncTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for agent connections.
 * Agents connect to /ws/agent/{agentId} and receive real-time task assignments.
 * This is a lightweight alternative to the STOMP-based browser WebSocket.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final AgentInfoRepository agentInfoRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final AgentCommunicationService agentCommunicationService;
    private final ObjectMapper objectMapper;

    /** Active agent WebSocket sessions, keyed by agentId. */
    private final ConcurrentHashMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String agentId = extractAgentId(session);
        if (agentId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        activeSessions.put(agentId, session);
        log.info("[AGENT_WS] Agent {} connected (session={})", agentId, session.getId());

        // Update agent status
        agentCommunicationService.onAgentConnected(agentId);

        // Send initial ping to verify connection
        sendMessage(session, "ping", Map.of("message", "connected"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String agentId = extractAgentId(session);
        if (agentId != null) {
            activeSessions.remove(agentId);
            log.info("[AGENT_WS] Agent {} disconnected (status={})", agentId, status);
            agentCommunicationService.onAgentDisconnected(agentId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String agentId = extractAgentId(session);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            switch (type != null ? type : "") {
                case "pong" -> {
                    // Heartbeat acknowledgment — update last seen
                    agentCommunicationService.heartbeat(agentId, Map.of("timestamp", System.currentTimeMillis()));
                }
                case "progress" -> {
                    // Task progress update from agent
                    Number taskId = (Number) payload.get("taskId");
                    Number progress = (Number) payload.get("progress");
                    String msg = (String) payload.get("message");
                    if (taskId != null && progress != null) {
                        agentCommunicationService.updateTaskProgress(
                                taskId.longValue(), progress.intValue(), msg != null ? msg : "");
                    }
                }
                case "complete" -> {
                    Number taskId = (Number) payload.get("taskId");
                    String result = (String) payload.get("result");
                    if (taskId != null) {
                        agentCommunicationService.completeTask(taskId.longValue(), result != null ? result : "");
                    }
                }
                case "fail" -> {
                    Number taskId = (Number) payload.get("taskId");
                    String error = (String) payload.get("error");
                    if (taskId != null) {
                        agentCommunicationService.failTask(taskId.longValue(), error != null ? error : "unknown error");
                    }
                }
                default -> log.debug("[AGENT_WS] Unknown message type from {}: {}", agentId, type);
            }
        } catch (Exception e) {
            log.warn("[AGENT_WS] Failed to handle message from {}: {}", agentId, e.getMessage());
        }
    }

    /**
     * Push a task to a specific agent via WebSocket.
     * Returns true if the message was sent successfully.
     */
    public boolean pushTask(String agentId, AsyncTask task) {
        WebSocketSession session = activeSessions.get(agentId);
        if (session == null || !session.isOpen()) {
            return false;
        }

        try {
            Map<String, Object> taskPayload = Map.of(
                    "id", task.getId(),
                    "type", task.getType().name(),
                    "status", task.getStatus().name(),
                    "progress", task.getProgress(),
                    "message", task.getMessage() != null ? task.getMessage() : ""
            );
            sendMessage(session, "task", taskPayload);
            log.info("[AGENT_WS] Pushed task {} to agent {}", task.getId(), agentId);
            return true;
        } catch (Exception e) {
            log.warn("[AGENT_WS] Failed to push task {} to agent {}: {}", task.getId(), agentId, e.getMessage());
            return false;
        }
    }

    /**
     * Send a cancel signal to an agent.
     */
    public boolean cancelTask(String agentId, Long taskId) {
        WebSocketSession session = activeSessions.get(agentId);
        if (session == null || !session.isOpen()) {
            return false;
        }

        try {
            sendMessage(session, "cancel", Map.of("taskId", taskId));
            return true;
        } catch (Exception e) {
            log.warn("[AGENT_WS] Failed to cancel task {} on agent {}: {}", taskId, agentId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if an agent is connected via WebSocket.
     */
    public boolean isAgentConnected(String agentId) {
        WebSocketSession session = activeSessions.get(agentId);
        return session != null && session.isOpen();
    }

    /**
     * Get count of connected agents.
     */
    public int getConnectedCount() {
        return (int) activeSessions.entrySet().stream()
                .filter(e -> e.getValue().isOpen())
                .count();
    }

    private void sendMessage(WebSocketSession session, String type, Object payload) throws IOException {
        Map<String, Object> envelope = Map.of("type", type, "payload", payload);
        String json = objectMapper.writeValueAsString(envelope);
        session.sendMessage(new TextMessage(json));
    }

    private String extractAgentId(WebSocketSession session) {
        String uri = session.getUri().getPath();
        // URI format: /ws/agent/{agentId}
        String[] parts = uri.split("/");
        if (parts.length >= 4) {
            return parts[3]; // agentId is the 4th segment
        }
        // Fallback: check query parameter
        String query = session.getUri().getQuery();
        if (query != null && query.contains("agentId=")) {
            for (String param : query.split("&")) {
                if (param.startsWith("agentId=")) {
                    return param.substring(8);
                }
            }
        }
        return null;
    }
}
