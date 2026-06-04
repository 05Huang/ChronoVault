package com.chronovault.service;

import com.chronovault.cache.CacheService;
import com.chronovault.docker.DockerOperationService;
import com.chronovault.dto.server.CreateContainerRequest;
import com.chronovault.entity.*;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.*;
import com.chronovault.security.CredentialEncryptor;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerServiceTest {

    @Mock private ServerRepository serverRepository;
    @Mock private ContainerRepository containerRepository;
    @Mock private VolumeRepository volumeRepository;
    @Mock private UserService userService;
    @Mock private DockerOperationService dockerService;
    @Mock private SshConnectionManager sshManager;
    @Mock private CredentialEncryptor credentialEncryptor;
    @Mock private CacheService cacheService;
    @Mock private SshConnection sshConnection;

    @InjectMocks
    private ServerService serverService;

    private User testUser;
    private Server testServer;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder().id(1L).email("test@example.com").build();
        testServer = Server.builder()
                .id(1L).user(testUser)
                .name("cell_tower").ip("152.32.215.7")
                .os("Ubuntu 22.04").status(Server.ServerStatus.RUNNING)
                .sshPort(22).sshUsername("ubuntu")
                .sshAuthMethod("KEY").sshKeyEncrypted("encrypted-key")
                .build();
    }

    @Test
    void getServers_returnsUserServers() {
        when(userService.getByEmail("test@example.com")).thenReturn(testUser);
        when(serverRepository.findByUserId(1L)).thenReturn(List.of(testServer));

        var result = serverService.getServers("test@example.com");

        assertEquals(1, result.size());
        assertEquals("cell_tower", result.get(0).name());
    }

    @Test
    void getServer_existing_returnsServer() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));

        var result = serverService.getServer(1L);
        assertEquals("cell_tower", result.name());
    }

    @Test
    void getServer_nonExisting_throwsNotFound() {
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> serverService.getServer(999L));
    }

    @Test
    void testConnection_noCredentials_returnsFailure() {
        Server noCreds = Server.builder()
                .id(2L).ip("10.0.0.1").sshPort(22).sshUsername("root")
                .sshKeyEncrypted(null).build();
        when(serverRepository.findById(2L)).thenReturn(Optional.of(noCreds));

        Map<String, Object> result = serverService.testConnection(2L);
        assertEquals(false, result.get("success"));
        assertTrue(result.get("message").toString().contains("未配置"));
    }

    @Test
    void testConnection_success_returnsOsInfo() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(sshConnection.executeCommand("uname -a"))
                .thenReturn(new SshConnection.CommandResult(0, "Linux cell 5.15.0 #1 SMP x86_64 GNU/Linux", ""));

        Map<String, Object> result = serverService.testConnection(1L);

        assertEquals(true, result.get("success"));
        assertTrue(result.get("osInfo").toString().contains("Linux"));
    }

    @Test
    void testConnection_timeout_returnsTimeoutMessage() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenThrow(new java.io.IOException("Connection timeout after 30000ms"));

        Map<String, Object> result = serverService.testConnection(1L);

        assertEquals(false, result.get("success"));
        assertTrue(result.get("message").toString().contains("超时"));
    }

    @Test
    void testConnection_authFail_returnsAuthErrorMessage() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenThrow(new java.io.IOException("Auth fail"));

        Map<String, Object> result = serverService.testConnection(1L);

        assertEquals(false, result.get("success"));
        assertTrue(result.get("message").toString().contains("认证失败"));
    }

    @Test
    void testConnection_connectionRefused_returnsRefusedMessage() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenThrow(new java.io.IOException("Connection refused"));

        Map<String, Object> result = serverService.testConnection(1L);

        assertEquals(false, result.get("success"));
        assertTrue(result.get("message").toString().contains("连接被拒绝"));
    }

    @Test
    void getContainers_emptyCache_refreshesFromDocker() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(containerRepository.findByServerId(1L)).thenReturn(List.of()).thenReturn(List.of(
                Container.builder().name("nginx").type(Container.ContainerType.NGINX).status(Container.ContainerStatus.RUNNING).build()
        ));
        when(dockerService.listContainers(testServer)).thenReturn(List.of(
                Container.builder().name("nginx").type(Container.ContainerType.NGINX).status(Container.ContainerStatus.RUNNING).build()
        ));

        var result = serverService.getContainers(1L);
        verify(dockerService).listContainers(testServer);
    }

    @Test
    void deleteServer_cleansUpConnectionsAndData() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));

        serverService.deleteServer(1L);

        verify(sshManager).removeConnection("152.32.215.7", 22);
        verify(containerRepository).deleteByServerId(1L);
        verify(volumeRepository).deleteByServerId(1L);
        verify(serverRepository).delete(testServer);
    }

    @Test
    void updateSshConfig_updatesFields() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        String validKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjEAAAA\n-----END OPENSSH PRIVATE KEY-----\n";
        when(credentialEncryptor.encrypt(anyString())).thenReturn("encrypted-new-key");

        serverService.updateSshConfig(1L, 2222, "root", "KEY", validKey);

        assertEquals(2222, testServer.getSshPort());
        assertEquals("root", testServer.getSshUsername());
        assertEquals("KEY", testServer.getSshAuthMethod());
        verify(credentialEncryptor).encrypt(anyString());
    }

    @Test
    void normalizeSshKey_singleLineKey_reconstructed() {
        String result = (String) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                serverService, "normalizeSshKey",
                "-----BEGIN OPENSSH PRIVATE KEY-----b3BlbnNzaC1rZXktdjEAAAA-----END OPENSSH PRIVATE KEY-----");

        assertNotNull(result);
        assertTrue(result.contains("-----BEGIN OPENSSH PRIVATE KEY-----\n"));
        assertTrue(result.contains("\n-----END OPENSSH PRIVATE KEY-----\n"));
    }

    @Test
    void normalizeSshKey_noMarkers_returnsNull() {
        String result = (String) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                serverService, "normalizeSshKey", "not-a-key");
        assertNull(result);
    }

    @Test
    void normalizeSshKey_null_returnsNull() {
        String result = (String) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                serverService, "normalizeSshKey", (String) null);
        assertNull(result);
    }

    // --- Docker Lifecycle Tests ---

    @Test
    void startContainer_success_returnsSuccessMap() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(dockerService.startContainer(testServer, "abc123")).thenReturn(true);

        Map<String, Object> result = serverService.startContainer(1L, "abc123");
        assertEquals(true, result.get("success"));
    }

    @Test
    void stopContainer_failure_returnsFailureMap() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(dockerService.stopContainer(testServer, "abc123")).thenReturn(false);

        Map<String, Object> result = serverService.stopContainer(1L, "abc123");
        assertEquals(false, result.get("success"));
    }

    @Test
    void createContainer_success_returnsContainerId() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(dockerService.createContainer(any(), eq("nginx:latest"), eq("myapp"), any(), any(), any()))
                .thenReturn("sha256:abc123");

        CreateContainerRequest req = new CreateContainerRequest("nginx:latest", "myapp", null, null, null);
        Map<String, Object> result = serverService.createContainer(1L, req);

        assertEquals(true, result.get("success"));
        assertEquals("sha256:abc123", result.get("containerId"));
    }

    @Test
    void getImages_returnsList() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(dockerService.listImages(testServer)).thenReturn(List.of(
                Map.of("repository", "nginx", "tag", "latest")
        ));

        var result = serverService.getImages(1L);
        assertEquals(1, result.size());
        assertEquals("nginx", result.get(0).get("repository"));
    }

    @Test
    void getNetworks_returnsList() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(dockerService.listNetworks(testServer)).thenReturn(List.of(
                Map.of("name", "bridge", "driver", "bridge")
        ));

        var result = serverService.getNetworks(1L);
        assertEquals(1, result.size());
    }
}
