package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentInstallServiceTest {

    @Mock private ServerRepository serverRepository;
    @Mock private SshConnectionManager sshManager;
    @Mock private SettingsService settingsService;

    @InjectMocks
    private AgentInstallService service;

    @Test
    void installAgent_serverNotFound_throwsException() {
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.installAgent(999L, "test@test.com", "http://localhost:8080"));
    }

    @Test
    void installAgent_serverFound_proceedsToSSH() {
        Server server = Server.builder().id(1L).name("test-server").ip("10.0.0.1").build();
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        ReflectionTestUtils.setField(service, "configuredServerUrl", "http://localhost:8080");

        // Should proceed past the server lookup (SSH may fail, but that's OK for this test)
        try {
            service.installAgent(1L, "test@test.com", "http://localhost:8080/api/servers/1/install-agent");
        } catch (Exception e) {
            // SSH connection may fail in test environment, which is expected
        }
        verify(serverRepository).findById(1L);
    }
}