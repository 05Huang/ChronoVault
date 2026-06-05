package com.chronovault.integration;

import com.chronovault.dto.auth.AuthResponse;
import com.chronovault.dto.auth.RegisterRequest;
import com.chronovault.repository.UserRepository;
import com.chronovault.security.JwtTokenProvider;
import com.chronovault.service.AuthService;
import com.chronovault.websocket.EventWebSocketHandler;
import com.chronovault.websocket.WebSocketConnectionTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for WebSocket connections and message push (STOMP over SockJS).
 *
 * Connection tests use SockJsClient (with WebSocket transport) because SockJS endpoints
 * only accept native WebSocket connections at the /websocket path suffix.
 * Component-level tests (tracker, event handler) test the beans directly without network.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private EventWebSocketHandler eventWebSocketHandler;
    @Autowired private WebSocketConnectionTracker connectionTracker;
    @LocalServerPort private int port;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    // ========== Helper ==========

    /**
     * Creates a STOMP client using raw WebSocket with JSON message converter.
     * Uses /ws/stomp endpoint which is a raw WebSocket STOMP endpoint.
     * Auth token is passed via query parameter.
     */
    private WebSocketStompClient createStompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        org.springframework.messaging.converter.MappingJackson2MessageConverter jsonConverter =
                new org.springframework.messaging.converter.MappingJackson2MessageConverter();
        jsonConverter.setPrettyPrint(false);
        client.setMessageConverter(jsonConverter);
        return client;
    }

    private String wsUrl(String token) {
        return "ws://localhost:" + port + "/ws/stomp?token=" + token;
    }

    // ========== STOMP Connection Tests ==========

    @Test
    void stompConnection_succeedsWithValidToken() throws Exception {
        AuthResponse auth = authService.register(
                new RegisterRequest("WS Test User", "ws-test@test.com", "password123"));
        String token = auth.token();

        WebSocketStompClient stompClient = createStompClient();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StompSession> sessionRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        stompClient.connect(wsUrl(token), new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                sessionRef.set(session);
                latch.countDown();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "WebSocket STOMP connection should succeed with valid token");

        if (errorRef.get() != null) {
            fail("Connection failed: " + errorRef.get().getMessage());
        }

        assertNotNull(sessionRef.get(), "Session should be established");
        assertTrue(sessionRef.get().isConnected(), "Session should be connected");

        stompClient.stop();
    }

    @Test
    void stompConnection_rejectedWithInvalidToken() throws Exception {
        AuthResponse auth = authService.register(
                new RegisterRequest("Attacker", "attacker@test.com", "password123"));
        String validToken = auth.token();
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";

        WebSocketStompClient stompClient = createStompClient();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StompSession> sessionRef = new AtomicReference<>();

        stompClient.connect(wsUrl(tamperedToken), new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                sessionRef.set(session);
                latch.countDown();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                latch.countDown();
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                                        StompHeaders headers, byte[] payload, Throwable exception) {
                latch.countDown();
            }
        });

        latch.await(10, TimeUnit.SECONDS);

        if (sessionRef.get() != null) {
            assertFalse(sessionRef.get().isConnected(),
                    "WebSocket connection should be rejected with invalid token");
        }
        // If session is null, connection was never established — also valid rejection

        stompClient.stop();
    }

    @Test
    void stompConnection_rejectedWithNoToken() throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StompSession> sessionRef = new AtomicReference<>();

        stompClient.connect("ws://localhost:" + port + "/ws/stomp", new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                sessionRef.set(session);
                latch.countDown();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                latch.countDown();
            }
        });

        latch.await(10, TimeUnit.SECONDS);

        if (sessionRef.get() != null) {
            assertFalse(sessionRef.get().isConnected(),
                    "WebSocket connection should fail without token");
        }

        stompClient.stop();
    }

    // ========== Message Broadcast Tests ==========

    @Test
    void stompSubscription_receivesBroadcastMessage() throws Exception {
        AuthResponse auth = authService.register(
                new RegisterRequest("Broadcast Test", "broadcast@test.com", "password123"));
        String token = auth.token();

        WebSocketStompClient stompClient = createStompClient();
        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(1);
        BlockingQueue<Map<?, ?>> receivedMessages = new LinkedBlockingQueue<>();
        AtomicReference<StompSession> sessionRef = new AtomicReference<>();

        stompClient.connect(wsUrl(token), new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                sessionRef.set(session);
                StompHeaders subHeaders = new StompHeaders();
                subHeaders.setDestination("/topic/events");
                session.subscribe(subHeaders, new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        receivedMessages.offer((Map<?, ?>) payload);
                        messageLatch.countDown();
                    }

                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }
                });
                connectLatch.countDown();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connectLatch.countDown();
            }
        });

        assertTrue(connectLatch.await(10, TimeUnit.SECONDS), "Should connect successfully");

        // Allow subscription to fully propagate through the broker
        Thread.sleep(2000);
        messagingTemplate.convertAndSend("/topic/events", Map.of(
                "type", "TEST_EVENT",
                "message", "Integration test broadcast",
                "level", "INFO"
        ));

        assertTrue(messageLatch.await(10, TimeUnit.SECONDS), "Should receive broadcast message");
        Map<?, ?> received = receivedMessages.poll(1, TimeUnit.SECONDS);
        assertNotNull(received);
        assertEquals("TEST_EVENT", received.get("type"), "Message should contain the event type");

        stompClient.stop();
    }

    @Test
    void stompSubscription_receivesServerSpecificMessage() throws Exception {
        AuthResponse auth = authService.register(
                new RegisterRequest("Server Topic Test", "server-topic@test.com", "password123"));
        String token = auth.token();

        WebSocketStompClient stompClient = createStompClient();
        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(1);
        BlockingQueue<Map<?, ?>> receivedMessages = new LinkedBlockingQueue<>();

        stompClient.connect(wsUrl(token), new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                StompHeaders subHeaders = new StompHeaders();
                subHeaders.setDestination("/topic/servers/1");
                session.subscribe(subHeaders, new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        receivedMessages.offer((Map<?, ?>) payload);
                        messageLatch.countDown();
                    }

                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }
                });
                connectLatch.countDown();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connectLatch.countDown();
            }
        });

        assertTrue(connectLatch.await(10, TimeUnit.SECONDS), "Should connect successfully");

        Thread.sleep(1500);
        messagingTemplate.convertAndSend("/topic/servers/1", Map.of(
                "event", "snapshot_completed",
                "serverId", 1L
        ));

        assertTrue(messageLatch.await(5, TimeUnit.SECONDS), "Should receive server-specific message");
        Map<?, ?> received = receivedMessages.poll(1, TimeUnit.SECONDS);
        assertNotNull(received);
        assertEquals("snapshot_completed", received.get("event"), "Should contain the snapshot event");

        stompClient.stop();
    }

    @Test
    void stompSubscription_doesNotReceiveUnsubscribedTopic() throws Exception {
        AuthResponse auth = authService.register(
                new RegisterRequest("Isolation Test", "isolation@test.com", "password123"));
        String token = auth.token();

        WebSocketStompClient stompClient = createStompClient();
        CountDownLatch connectLatch = new CountDownLatch(1);
        BlockingQueue<Map<?, ?>> receivedMessages = new LinkedBlockingQueue<>();

        stompClient.connect(wsUrl(token), new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                connectLatch.countDown();
                session.subscribe("/topic/events", new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        receivedMessages.offer((Map<?, ?>) payload);
                    }

                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }
                });
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connectLatch.countDown();
            }
        });

        assertTrue(connectLatch.await(10, TimeUnit.SECONDS), "Should connect successfully");

        Thread.sleep(500);
        messagingTemplate.convertAndSend("/topic/servers/999", Map.of(
                "event", "should_not_arrive"
        ));

        Thread.sleep(1000);
        assertTrue(receivedMessages.isEmpty(),
                "Should NOT receive messages from unsubscribed topics");

        stompClient.stop();
    }

    // ========== Multiple Client Broadcast Test ==========

    @Test
    void stompMultipleClients_bothReceiveBroadcast() throws Exception {
        AuthResponse auth1 = authService.register(
                new RegisterRequest("User A", "multi-a@test.com", "password123"));
        AuthResponse auth2 = authService.register(
                new RegisterRequest("User B", "multi-b@test.com", "password123"));

        WebSocketStompClient stompClient1 = createStompClient();
        WebSocketStompClient stompClient2 = createStompClient();

        CountDownLatch connectLatch1 = new CountDownLatch(1);
        CountDownLatch connectLatch2 = new CountDownLatch(1);
        CountDownLatch messageLatch1 = new CountDownLatch(1);
        CountDownLatch messageLatch2 = new CountDownLatch(1);

        stompClient1.connect(wsUrl(auth1.token()), new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                StompHeaders subHeaders = new StompHeaders();
                subHeaders.setDestination("/topic/events");
                session.subscribe(subHeaders, new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        messageLatch1.countDown();
                    }

                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }
                });
                connectLatch1.countDown();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connectLatch1.countDown();
            }
        });

        stompClient2.connect(wsUrl(auth2.token()), new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                StompHeaders subHeaders = new StompHeaders();
                subHeaders.setDestination("/topic/events");
                session.subscribe(subHeaders, new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        messageLatch2.countDown();
                    }

                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }
                });
                connectLatch2.countDown();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connectLatch2.countDown();
            }
        });

        assertTrue(connectLatch1.await(10, TimeUnit.SECONDS), "Client 1 should connect");
        assertTrue(connectLatch2.await(10, TimeUnit.SECONDS), "Client 2 should connect");

        Thread.sleep(1500);
        messagingTemplate.convertAndSend("/topic/events", Map.of("type", "MULTI_CLIENT_TEST"));

        assertTrue(messageLatch1.await(5, TimeUnit.SECONDS), "Client 1 should receive broadcast");
        assertTrue(messageLatch2.await(5, TimeUnit.SECONDS), "Client 2 should receive broadcast");

        stompClient1.stop();
        stompClient2.stop();
    }

    // ========== EventWebSocketHandler Direct Tests ==========

    @Test
    void eventHandler_sendToTopic_doesNotThrow() {
        assertDoesNotThrow(() -> {
            eventWebSocketHandler.sendToTopic("/topic/events", Map.of("test", true));
            eventWebSocketHandler.sendToTopic("/topic/servers/1", Map.of("test", true));
            eventWebSocketHandler.sendToTopic("/topic/events/info", Map.of("test", true));
            eventWebSocketHandler.sendToTopic("/topic/events/source/test", Map.of("test", true));
        }, "sendToTopic should not throw for any valid topic pattern");
    }

    @Test
    void eventHandler_sendServerEvent_doesNotThrow() {
        assertDoesNotThrow(() -> {
            eventWebSocketHandler.sendServerEvent(1L, Map.of("event", "snapshot_done"));
            eventWebSocketHandler.sendServerEvent(42L, Map.of("event", "backup_started"));
        }, "sendServerEvent should not throw");
    }

    // ========== WebSocketConnectionTracker Tests ==========

    @Test
    void connectionTracker_tracksAndRemovesConnections() {
        int initialCount = connectionTracker.getActiveConnections();

        connectionTracker.trackConnection("session-1", "user1@test.com");
        assertEquals(initialCount + 1, connectionTracker.getActiveConnections());
        assertTrue(connectionTracker.getConnectedUsers().contains("user1@test.com"));

        connectionTracker.trackConnection("session-2", "user2@test.com");
        assertEquals(initialCount + 2, connectionTracker.getActiveConnections());

        connectionTracker.removeConnection("session-1");
        assertEquals(initialCount + 1, connectionTracker.getActiveConnections());
        assertFalse(connectionTracker.getConnectedUsers().contains("user1@test.com"));
        assertTrue(connectionTracker.getConnectedUsers().contains("user2@test.com"));

        connectionTracker.removeConnection("session-2");
        assertEquals(initialCount, connectionTracker.getActiveConnections());
    }

    @Test
    void connectionTracker_removeNonexistent_isNoOp() {
        int countBefore = connectionTracker.getActiveConnections();
        connectionTracker.removeConnection("nonexistent-session");
        assertEquals(countBefore, connectionTracker.getActiveConnections(),
                "Removing nonexistent session should not change count");
    }

    @Test
    void connectionTracker_getConnections_returnsSnapshot() {
        connectionTracker.trackConnection("snap-session", "snapshot@test.com");
        Map<String, String> connections = connectionTracker.getConnections();
        assertNotNull(connections);
        assertTrue(connections.containsKey("snap-session"));
        assertEquals("snapshot@test.com", connections.get("snap-session"));
        connectionTracker.removeConnection("snap-session");
    }

    // ========== STOMP Protocol Error Handling Tests ==========

    @Test
    void stompConnection_handlesTransportErrorGracefully() throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        stompClient.connect(wsUrl("definitely-invalid-token"), new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorRef.set(exception);
                latch.countDown();
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                                        StompHeaders headers, byte[] payload, Throwable exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        });

        latch.await(10, TimeUnit.SECONDS);
        assertNotNull(errorRef.get(), "Should have captured transport error for invalid token");
        stompClient.stop();
    }
}
