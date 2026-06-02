package com.chronovault.service;

import com.chronovault.dto.drift.DriftReportDTO;
import com.chronovault.entity.Server;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriftDetectionServiceTest {

    @Mock private ServerRepository serverRepository;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private SshConnectionManager sshManager;
    @Mock private SshConnection sshConnection;

    @InjectMocks
    private DriftDetectionService driftDetectionService;

    private Server testServer;

    @BeforeEach
    void setUp() {
        testServer = Server.builder().id(1L).name("Test Server").ip("192.168.1.1").status(Server.ServerStatus.RUNNING).build();
    }

    @Test
    void detectDrift_validServer_returnsReport() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(sshConnection.executeCommand(anyString())).thenReturn(new SshConnection.CommandResult(0, "", ""));

        DriftReportDTO result = driftDetectionService.detectDrift(1L);

        assertNotNull(result);
        assertEquals(1L, result.serverId());
        assertEquals("Test Server", result.serverName());
    }

    @Test
    void detectDrift_nonExistingServer_throwsException() {
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> driftDetectionService.detectDrift(999L));
    }

    @Test
    void detectDrift_sshConnectionFails_throwsBadRequest() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(BadRequestException.class, () -> driftDetectionService.detectDrift(1L));
    }

    @Test
    void detectDrift_cleanServer_returnsCleanStatus() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(sshConnection.executeCommand(anyString())).thenReturn(new SshConnection.CommandResult(0, "", ""));

        DriftReportDTO result = driftDetectionService.detectDrift(1L);

        assertEquals("CLEAN", result.status());
        assertEquals(0, result.totalChanges());
    }

    @Test
    void detectDrift_withUnhealthyContainers_returnsDrift() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);

        lenient().when(sshConnection.executeCommand(contains("docker ps --format"))).thenReturn(new SshConnection.CommandResult(0, "nginx|nginx:latest|Up 1 hour", ""));
        lenient().when(sshConnection.executeCommand(contains("health=unhealthy"))).thenReturn(new SshConnection.CommandResult(0, "nginx", ""));
        lenient().when(sshConnection.executeCommand(contains("test -f"))).thenReturn(new SshConnection.CommandResult(1, "", ""));
        lenient().when(sshConnection.executeCommand(contains("ss -tlnp"))).thenReturn(new SshConnection.CommandResult(0, "", ""));

        DriftReportDTO result = driftDetectionService.detectDrift(1L);

        assertNotNull(result);
        assertTrue(result.totalChanges() > 0);
    }

    @Test
    void detectDrift_withListeningPorts_returnsPortDrift() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);

        lenient().when(sshConnection.executeCommand(contains("docker ps --format"))).thenReturn(new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(contains("health=unhealthy"))).thenReturn(new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(contains("test -f"))).thenReturn(new SshConnection.CommandResult(1, "", ""));
        lenient().when(sshConnection.executeCommand(contains("ss -tlnp"))).thenReturn(new SshConnection.CommandResult(0, "LISTEN 0 128 *:80 *:* users:((nginx))", ""));

        DriftReportDTO result = driftDetectionService.detectDrift(1L);

        assertNotNull(result);
    }

    @Test
    void detectDrift_withConfigFiles_returnsFileDrift() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);

        lenient().when(sshConnection.executeCommand(contains("docker ps --format"))).thenReturn(new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(contains("health=unhealthy"))).thenReturn(new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(contains("test -f"))).thenReturn(new SshConnection.CommandResult(0, "abc123hash", ""));
        lenient().when(sshConnection.executeCommand(contains("ss -tlnp"))).thenReturn(new SshConnection.CommandResult(0, "", ""));

        DriftReportDTO result = driftDetectionService.detectDrift(1L);

        assertNotNull(result);
    }

    @Test
    void detectDrift_manyChanges_returnsChangedStatus() throws Exception {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);

        lenient().when(sshConnection.executeCommand(contains("docker ps --format"))).thenReturn(new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(contains("health=unhealthy"))).thenReturn(new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(contains("test -f"))).thenReturn(new SshConnection.CommandResult(0, "abc", ""));
        lenient().when(sshConnection.executeCommand(contains("ss -tlnp"))).thenReturn(new SshConnection.CommandResult(0,
                "LISTEN 0 128 *:80 *:* users:((nginx))\nLISTEN 0 128 *:443 *:* users:((nginx))\nLISTEN 0 128 *:3306 *:* users:((mysql))", ""));

        DriftReportDTO result = driftDetectionService.detectDrift(1L);

        assertNotNull(result);
        assertTrue(result.totalChanges() >= 3);
        assertEquals("CHANGED", result.status());
    }
}
