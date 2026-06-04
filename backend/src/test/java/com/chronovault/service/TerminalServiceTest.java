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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TerminalServiceTest {

    @Mock private ServerRepository serverRepository;
    @Mock private SshConnectionManager sshManager;

    @InjectMocks
    private TerminalService service;

    @Test
    void createSession_serverNotFound_throwsException() {
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createSession(999L, "test@test.com"));
    }

    @Test
    void executeCommand_nonExistingSession_returnsError() {
        var result = service.executeCommand("nonexistent-session", "ls -la");
        assertTrue(result.containsKey("error"));
    }

    @Test
    void closeSession_nonExistingSession_doesNotThrow() {
        // Should not throw when closing a non-existing session
        assertDoesNotThrow(() -> service.closeSession("nonexistent"));
    }
}