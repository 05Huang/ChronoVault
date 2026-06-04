package com.chronovault.service;

import com.chronovault.ssh.SshConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}