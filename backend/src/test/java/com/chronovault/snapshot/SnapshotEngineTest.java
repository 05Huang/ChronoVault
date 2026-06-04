package com.chronovault.snapshot;

import com.chronovault.entity.*;
import com.chronovault.repository.*;
import com.chronovault.service.StateCollectionService;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.storage.StorageRouter;
import com.chronovault.task.AsyncTaskManager;
import com.chronovault.task.TaskType;
import com.chronovault.service.SnapshotHookService;
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

/**
 * Unit tests for SnapshotEngine — verifies the snapshot creation flow
 * with mocked SSH and Restic dependencies.
 */
@ExtendWith(MockitoExtension.class)
class SnapshotEngineTest {

    @Mock private SshConnectionManager sshManager;
    @Mock private ResticClient resticClient;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private SnapshotManifestRepository manifestRepository;
    @Mock private ContainerStateRepository containerStateRepository;
    @Mock private SnapshotHookService hookService;
    @Mock private StorageRouter storageRouter;
    @Mock private AsyncTaskManager taskManager;
    @Mock private StateCollectionService stateCollectionService;
    @Mock private SshConnection sshConnection;
    @Mock private com.chronovault.service.SnapshotService snapshotServiceRef;

    @InjectMocks
    private SnapshotEngine snapshotEngine;

    private Server testServer;
    private StorageTarget testStorageTarget;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(snapshotEngine, "resticPassword", "test-password-123");
        ReflectionTestUtils.setField(snapshotEngine, "snapshotServiceRef", snapshotServiceRef);

        testServer = Server.builder()
                .id(1L).name("test-server").ip("192.168.1.100")
                .sshPort(22).sshUsername("root").sshAuthMethod("KEY")
                .status(Server.ServerStatus.RUNNING)
                .build();

        testStorageTarget = StorageTarget.builder()
                .id(1L).name("local-storage").type(StorageTarget.StorageType.LOCAL)
                .endpoint("/data/backups").build();
    }

    @Test
    void createSnapshot_savesAndSubmitsTask() {
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });

        Snapshot result = snapshotEngine.createSnapshot(
                testServer, testStorageTarget, "Test Snapshot", "test note",
                Snapshot.SnapshotType.FULL, 1L, null, null);

        assertNotNull(result);
        assertEquals("Test Snapshot", result.getTitle());
        assertEquals("test note", result.getNote());
        verify(snapshotRepository).save(any(Snapshot.class));
        verify(taskManager).submit(eq(TaskType.SNAPSHOT), eq(1L), eq(1L),
                anyString(), any());
    }

    @Test
    void validateConfig_blankPassword_throwsException() {
        // Test the config validation logic directly using the injected engine
        ReflectionTestUtils.setField(snapshotEngine, "resticPassword", "");
        assertThrows(IllegalStateException.class, snapshotEngine::validateConfig);
        // Restore valid password for other tests
        ReflectionTestUtils.setField(snapshotEngine, "resticPassword", "test-password-123");
    }

    @Test
    void validateConfig_nullPassword_throwsException() {
        ReflectionTestUtils.setField(snapshotEngine, "resticPassword", null);
        assertThrows(IllegalStateException.class, snapshotEngine::validateConfig);
        // Restore valid password for other tests
        ReflectionTestUtils.setField(snapshotEngine, "resticPassword", "test-password-123");
    }
}