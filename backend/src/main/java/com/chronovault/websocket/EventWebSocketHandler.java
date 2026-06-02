package com.chronovault.websocket;

import com.chronovault.entity.Event;
import com.chronovault.repository.EventRepository;
import com.chronovault.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final EventRepository eventRepository;

    public void broadcastEvent(Event event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", event.getId());
        payload.put("level", event.getLevel().name());
        payload.put("message", event.getMessage());
        payload.put("source", event.getSource());
        if (event.getTask() != null) {
            payload.put("taskId", event.getTask().getId());
        }
        payload.put("createdAt", event.getCreatedAt().toString());

        // Broadcast to general topic
        messagingTemplate.convertAndSend("/topic/events", payload);

        // Broadcast to event-type-specific topic for filtering
        String eventType = event.getLevel().name().toLowerCase();
        messagingTemplate.convertAndSend("/topic/events/" + eventType, payload);

        // Broadcast to source-specific topic if available
        if (event.getSource() != null && !event.getSource().isBlank()) {
            messagingTemplate.convertAndSend("/topic/events/source/" + event.getSource(), payload);
        }
    }

    public void sendToTopic(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void sendServerEvent(Long serverId, Object payload) {
        messagingTemplate.convertAndSend("/topic/servers/" + serverId, payload);
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        try {
            List<Event> recentEvents = eventRepository.findAllByOrderByCreatedAtDesc();
            if (!recentEvents.isEmpty()) {
                Event latest = recentEvents.get(0);
                Map<String, Object> heartbeat = new HashMap<>();
                heartbeat.put("type", "HEARTBEAT");
                heartbeat.put("latestEventId", latest.getId());
                heartbeat.put("timestamp", LocalDateTime.now().toString());
                messagingTemplate.convertAndSend("/topic/events", heartbeat);
            }
        } catch (Exception e) {
            log.debug("Heartbeat skipped: {}", e.getMessage());
        }
    }
}
