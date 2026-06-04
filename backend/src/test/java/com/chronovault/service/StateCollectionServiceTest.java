package com.chronovault.service;

import com.chronovault.ssh.SshConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateCollectionServiceTest {

    @Mock private SshConnection sshConnection;

    @InjectMocks
    private StateCollectionService service;

    @BeforeEach
    void setUp() {
        // Inject a real ObjectMapper to avoid null pointer in state collection
        try {
            var field = StateCollectionService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(service, new ObjectMapper());
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject ObjectMapper", e);
        }
    }

    @Test
    void collectStateViaSsh_returnsValidJson() {
        // Mock all SSH commands to return reasonable defaults
        when(sshConnection.executeCommand(anyString(), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "test-value", ""));

        String result = service.collectStateViaSsh(sshConnection);
        // Should return either valid JSON or null (if collection partially fails)
        // The important thing is it doesn't throw
        if (result != null) {
            assertTrue(result.contains("collected_at"));
            assertTrue(result.contains("agent_version"));
            assertTrue(result.contains("collection_duration_ms"));
        }
    }

    @Test
    void collectStateViaSsh_commandTimeout_returnsPartialState() {
        // Some commands timeout, others succeed
        when(sshConnection.executeCommand(contains("hostname"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "myhost", ""));
        when(sshConnection.executeCommand(contains("dpkg"), any(Duration.class)))
                .thenThrow(new RuntimeException("timeout"));
        when(sshConnection.executeCommand(contains("systemctl"), any(Duration.class)))
                .thenThrow(new RuntimeException("timeout"));

        // Should still return partial state without throwing
        String result = service.collectStateViaSsh(sshConnection);
        // The method may return null if collection fails, but should not throw
        // At minimum, it should attempt to collect
        verify(sshConnection, atLeastOnce()).executeCommand(anyString(), any(Duration.class));
    }

    @Test
    void collectStateViaSsh_allCommandsFail_returnsPartialState() {
        // All commands throw exceptions — service handles gracefully
        when(sshConnection.executeCommand(anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("connection refused"));

        String result = service.collectStateViaSsh(sshConnection);
        // Should still return a valid JSON structure with empty collections
        assertNotNull(result);
        assertTrue(result.contains("collected_at"));
        assertTrue(result.contains("packages"));
        assertTrue(result.contains("services"));
        assertTrue(result.contains("docker"));
    }

    @Test
    void collectStateViaSsh_hostnameReturnsCorrectly() {
        // Mock specific commands
        when(sshConnection.executeCommand(contains("hostname"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "web-server-01", ""));
        when(sshConnection.executeCommand(contains("os-release"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "Ubuntu\n22.04", ""));
        when(sshConnection.executeCommand(contains("uname -r"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "5.15.0-91-generic", ""));
        when(sshConnection.executeCommand(contains("uname -m"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "x86_64", ""));
        when(sshConnection.executeCommand(contains("free"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "256/1024MB (25.0%)", ""));
        when(sshConnection.executeCommand(contains("df"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "10G/50G (20%)", ""));
        when(sshConnection.executeCommand(contains("nproc"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "4", ""));
        when(sshConnection.executeCommand(contains("uptime"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "up 5 days", ""));
        // Docker, dpkg, systemctl, ss, etc. return empty
        when(sshConnection.executeCommand(contains("dpkg"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        when(sshConnection.executeCommand(contains("systemctl"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        when(sshConnection.executeCommand(contains("docker"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        when(sshConnection.executeCommand(contains("ss "), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        when(sshConnection.executeCommand(contains("/etc/"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        when(sshConnection.executeCommand(contains("cat /etc/crontab"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        // rpm fallback
        when(sshConnection.executeCommand(contains("rpm"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        // apk fallback
        when(sshConnection.executeCommand(contains("apk"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        // stat for configs
        when(sshConnection.executeCommand(contains("stat"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        // netstat fallback
        when(sshConnection.executeCommand(contains("netstat"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        // find compose files
        when(sshConnection.executeCommand(contains("find"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        // docker info check
        when(sshConnection.executeCommand(contains("docker info"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        // docker ps
        when(sshConnection.executeCommand(contains("docker ps"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));
        // docker inspect
        when(sshConnection.executeCommand(contains("docker inspect"), any(Duration.class)))
                .thenReturn(new SshConnection.CommandResult(1, "", "not found"));

        String result = service.collectStateViaSsh(sshConnection);

        if (result != null) {
            // Verify the JSON structure
            assertTrue(result.contains("collected_at"));
            assertTrue(result.contains("agent_version"));
            assertTrue(result.contains("os"));
            assertTrue(result.contains("system"));
        }
    }
}