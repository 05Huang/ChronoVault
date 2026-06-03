package com.chronovault.controller;

import com.chronovault.dto.server.*;
import com.chronovault.entity.Server;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.service.*;
import com.chronovault.ai.AiAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerControllerTest {

    @Mock private ServerService serverService;
    @Mock private ServerHealthMonitor healthMonitor;
    @Mock private AiAnalysisService aiAnalysisService;
    @Mock private AgentInstallService agentInstallService;
    @Mock private ServerCloneService cloneService;
    @Mock private AutoSnapshotService autoSnapshotService;
    @Mock private UserService userService;

    @InjectMocks
    private ServerController controller;

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("test@test.com", null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
    }

    private ServerDTO createTestServer(Long id, String name) {
        return new ServerDTO(id, name, "192.168.1.1", "Ubuntu 22.04", Server.ServerStatus.RUNNING.name(),
                "0 天 0 小时", 0L, 22, "root", "KEY", false, null);
    }

    @Test
    void getServers_returnsList() {
        when(serverService.getServers("test@test.com")).thenReturn(List.of(createTestServer(1L, "Server 1")));

        var response = controller.getServers(auth());
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().data().size());
        assertEquals("Server 1", response.getBody().data().get(0).name());
    }

    @Test
    void getServer_validId_returnsServer() {
        when(serverService.getServer(1L)).thenReturn(createTestServer(1L, "My Server"));

        var response = controller.getServer(1L);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("My Server", response.getBody().data().name());
    }

    @Test
    void getServer_nonExistent_throws() {
        when(serverService.getServer(999L)).thenThrow(new ResourceNotFoundException("服务器不存在"));

        assertThrows(ResourceNotFoundException.class, () -> controller.getServer(999L));
    }

    @Test
    void deleteServer_validId_succeeds() {
        doNothing().when(serverService).deleteServer(1L);

        assertDoesNotThrow(() -> controller.deleteServer(1L));
        verify(serverService).deleteServer(1L);
    }

    @Test
    void toggleAutoSnapshot_enabled_succeeds() {
        doNothing().when(autoSnapshotService).setAutoSnapshotEnabled(1L, true);

        var response = controller.toggleAutoSnapshot(1L, new ToggleAutoSnapshotRequest(true));
        assertEquals(200, response.getStatusCode().value());
        verify(autoSnapshotService).setAutoSnapshotEnabled(1L, true);
    }

    @Test
    void connect_validId_returnsConnectionInfo() {
        when(serverService.getServer(1L)).thenReturn(
                new ServerDTO(1L, "Server", "10.0.0.1", "Ubuntu", "RUNNING",
                        "0 天 0 小时", 0L, 22, "root", "KEY", false, null));

        var response = controller.connect(1L);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("10.0.0.1", response.getBody().data().get("host"));
        assertEquals("22", response.getBody().data().get("port"));
    }

    @Test
    void batchScan_validIds_succeeds() {
        when(serverService.batchScan(List.of(1L, 2L))).thenReturn(2);

        var response = controller.batchScan(new BatchScanRequest(List.of(1L, 2L)));
        assertEquals(200, response.getStatusCode().value());
        verify(serverService).batchScan(List.of(1L, 2L));
    }

    @Test
    void createServer_validRequest_succeeds() {
        when(serverService.createServer("test@test.com", "New Server", "10.0.0.1", "Ubuntu"))
                .thenReturn(createTestServer(3L, "New Server"));

        CreateServerRequest request = new CreateServerRequest("New Server", "10.0.0.1", "Ubuntu");
        var response = controller.createServer(auth(), request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("New Server", response.getBody().data().name());
    }
}