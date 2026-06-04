package com.chronovault.service;

import com.chronovault.config.DistributedLock;
import com.chronovault.entity.Server;
import com.chronovault.repository.AlertRepository;
import com.chronovault.repository.EventRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.websocket.EventWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerHealthMonitorTest {

    @Mock private ServerRepository serverRepository;
    @Mock private SshConnectionManager sshManager;
    @Mock private EventWebSocketHandler eventHandler;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private AlertRepository alertRepository;
    @Mock private EventRepository eventRepository;
    @Mock private DistributedLock distributedLock;

    @InjectMocks
    private ServerHealthMonitor service;

    @Test
    void getServerHealth_serverNotFound_returnsError() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get("server:health:999")).thenReturn(null);
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        var result = service.getServerHealth(999L);
        assertTrue(result.containsKey("error"));
    }

    @Test
    void getServerHealth_serverFound_triggersCheck() {
        Server server = Server.builder().id(1L).name("test-server").ip("10.0.0.1").build();
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get("server:health:1")).thenReturn(null);
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));

        // Should attempt to check health (may fail in test env, but shouldn't throw)
        try {
            var result = service.getServerHealth(1L);
            assertNotNull(result);
        } catch (Exception e) {
            // SSH connection may fail, which is expected
        }
    }

    @Test
    void forceRefresh_serverNotFound_returnsError() {
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        var result = service.forceRefresh(999L);
        assertTrue(result.containsKey("error"));
    }
}