package com.chronovault.service;

import com.chronovault.dto.recovery.ExecuteRequest;
import com.chronovault.dto.recovery.JobStatusDTO;
import com.chronovault.dto.recovery.MigrateRequest;
import com.chronovault.dto.recovery.SimulateRequest;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.entity.AsyncTask;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.task.AsyncTaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    @Mock private SnapshotRepository snapshotRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private AsyncTaskManager taskManager;
    @Mock private SshConnectionManager sshManager;
    @Mock private ResticClient resticClient;
    @Mock private SshConnection sshConnection;

    @InjectMocks
    private RecoveryService recoveryService;

    private Snapshot testSnapshot;
    private Server testServer;
    private StorageTarget testStorageTarget;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recoveryService, "resticPassword", "test-password");
        testServer = Server.builder().id(1L).name("Test Server").ip("192.168.1.1").status(Server.ServerStatus.RUNNING).build();
        testSnapshot = Snapshot.builder().id(1L).server(testServer).title("Test Snapshot").hash("abc123").status(Snapshot.SnapshotStatus.STABLE).sizeBytes(1024L * 1024).build();
        testStorageTarget = StorageTarget.builder().id(1L).type(StorageTarget.StorageType.LOCAL).endpoint("/backup").build();
    }

    @Test
    void simulate_validRequest_returnsSuccess() throws Exception {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(testSnapshot));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        when(storageTargetRepository.findAll()).thenReturn(List.of(testStorageTarget));
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/backup");
        when(resticClient.dryRunRestore(eq(sshConnection), eq("/backup"), eq("test-password"), eq("abc123"))).thenReturn(true);

        JobStatusDTO result = recoveryService.simulate(new SimulateRequest(1L, 1L));

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
    }

    @Test
    void simulate_nonExistingSnapshot_throwsException() {
        when(snapshotRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> recoveryService.simulate(new SimulateRequest(999L, 1L)));
    }

    @Test
    void simulate_nonExistingServer_throwsException() {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(testSnapshot));
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> recoveryService.simulate(new SimulateRequest(1L, 999L)));
    }

    @Test
    void simulate_noStorageTargets_returnsFailure() throws Exception {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(testSnapshot));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        when(storageTargetRepository.findAll()).thenReturn(List.of());

        JobStatusDTO result = recoveryService.simulate(new SimulateRequest(1L, 1L));

        assertEquals("FAILED", result.status());
        assertTrue(result.estimatedTime().contains("存储目标"));
    }

    @Test
    void simulate_dryRunFails_returnsFailure() throws Exception {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(testSnapshot));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        when(storageTargetRepository.findAll()).thenReturn(List.of(testStorageTarget));
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/backup");
        when(resticClient.dryRunRestore(eq(sshConnection), eq("/backup"), eq("test-password"), eq("abc123"))).thenReturn(false);

        JobStatusDTO result = recoveryService.simulate(new SimulateRequest(1L, 1L));

        assertEquals("FAILED", result.status());
    }

    @Test
    void execute_validRequest_returnsRunning() {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(testSnapshot));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        AsyncTask mockTask = AsyncTask.builder().id(1L).build();
        when(taskManager.submit(any(), anyLong(), isNull(), anyString(), any())).thenReturn(mockTask);

        JobStatusDTO result = recoveryService.execute(new ExecuteRequest(1L, 1L, "full"));

        assertNotNull(result);
        assertEquals("RUNNING", result.status());
    }

    @Test
    void migrate_validRequest_returnsRunning() {
        Server targetServer = Server.builder().id(2L).name("Target Server").ip("192.168.1.2").status(Server.ServerStatus.RUNNING).build();
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(serverRepository.findById(2L)).thenReturn(Optional.of(targetServer));
        AsyncTask mockTask = AsyncTask.builder().id(1L).build();
        when(taskManager.submit(any(), anyLong(), isNull(), anyString(), any())).thenReturn(mockTask);

        JobStatusDTO result = recoveryService.migrate(new MigrateRequest(1L, 2L, null));

        assertNotNull(result);
        assertEquals("RUNNING", result.status());
    }

    @Test
    void getTaskStatus_nonExistingTask_returnsNotFound() {
        when(taskManager.getStatus(999L)).thenReturn(null);

        JobStatusDTO result = recoveryService.getTaskStatus(999L);

        assertEquals("NOT_FOUND", result.status());
    }
}
