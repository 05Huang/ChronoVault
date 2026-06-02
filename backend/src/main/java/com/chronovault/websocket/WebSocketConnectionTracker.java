package com.chronovault.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketConnectionTracker {

    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public void trackConnection(String sessionId, String email) {
        sessions.put(sessionId, email);
        log.debug("WebSocket connection tracked: session={}, user={}", sessionId, email);
    }

    public void removeConnection(String sessionId) {
        String removed = sessions.remove(sessionId);
        if (removed != null) {
            log.debug("WebSocket connection removed: session={}, user={}", sessionId, removed);
        }
    }

    public int getActiveConnections() {
        return sessions.size();
    }

    public Set<String> getConnectedUsers() {
        return Set.copyOf(sessions.values());
    }

    public Map<String, String> getConnections() {
        return Map.copyOf(sessions);
    }
}