package com.chronovault.controller;

import com.chronovault.dto.terminal.TerminalExecRequest;
import com.chronovault.service.TerminalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TerminalControllerTest {

    @Mock private TerminalService terminalService;

    @InjectMocks
    private TerminalController controller;

    @Test
    void createSession_returnsSessionId() {
        when(terminalService.createSession(1L, "test@test.com")).thenReturn("session-123");
        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.createSession(auth, 1L);
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void executeCommand_returnsResult() {
        Map<String, Object> result = Map.of("output", "total 0", "exitCode", 0);
        when(terminalService.executeCommand("session-123", "ls -la")).thenReturn(result);
        TerminalExecRequest body = new TerminalExecRequest("ls -la");
        var response = controller.executeCommand("session-123", body);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void closeSession_succeeds() {
        doNothing().when(terminalService).closeSession("session-123");
        var response = controller.closeSession("session-123");
        assertEquals(200, response.getStatusCode().value());
        verify(terminalService).closeSession("session-123");
    }
}