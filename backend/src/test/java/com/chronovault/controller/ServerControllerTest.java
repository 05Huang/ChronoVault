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
        assertEquals(201, response.getStatusCode().value());
        assertEquals("New Server", response.getBody().data().name());
    }

    // ===== cloneServer test =====

    @Test
    void cloneServer_validRequest_succeeds() {
        when(userService.getByEmail("test@test.com")).thenReturn(
                com.chronovault.entity.User.builder().id(1L).email("test@test.com").build());
        doNothing().when(cloneService).cloneServer(any(), eq(1L));

        CloneServerRequest request = new CloneServerRequest(1L, "10.0.0.2", "Cloned Server", 22, "root");
        var response = controller.cloneServer(auth(), request);
        assertEquals(200, response.getStatusCode().value());
    }

    // ===== getContainers test =====

    @Test
    void getContainers_validServer_returnsList() {
        when(serverService.getContainers(1L)).thenReturn(List.of());
        var response = controller.getContainers(1L);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().data());
    }

    // ===== getVolumes test =====

    @Test
    void getVolumes_validServer_returnsList() {
        when(serverService.getVolumes(1L)).thenReturn(List.of());
        var response = controller.getVolumes(1L);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().data());
    }

    // ===== addVolume test =====

    @Test
    void addVolume_validRequest_returnsCreated() {
        VolumeDTO volume = new VolumeDTO(1L, "data", "/host/data", "data", 0L, "", "active");
        when(serverService.addVolume(1L, "/data", "/host/data")).thenReturn(volume);

        AddVolumeRequest request = new AddVolumeRequest("/data", "/host/data");
        var response = controller.addVolume(1L, request);
        assertEquals(201, response.getStatusCode().value());
    }

    // ===== getLogs test =====

    @Test
    void getLogs_validServer_returnsList() {
        when(serverService.getLogs(1L, 100)).thenReturn(List.of());
        var response = controller.getLogs(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    // ===== clearLogs test =====

    @Test
    void clearLogs_validServer_succeeds() {
        doNothing().when(serverService).clearLogs(1L);
        var response = controller.clearLogs(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    // ===== updateSshConfig test =====

    @Test
    void updateSshConfig_validRequest_succeeds() {
        ServerDTO updated = createTestServer(1L, "Server");
        when(serverService.updateSshConfig(1L, 22, "root", "KEY", null)).thenReturn(updated);

        UpdateSshConfigRequest request = new UpdateSshConfigRequest(22, "root", "KEY", null);
        var response = controller.updateSshConfig(1L, request);
        assertEquals(200, response.getStatusCode().value());
    }

    // ===== testConnection test =====

    @Test
    void testConnection_validServer_returnsResult() {
        when(serverService.testConnection(1L)).thenReturn(java.util.Map.of("success", true));
        var response = controller.testConnection(1L);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().data().get("success"));
    }
}