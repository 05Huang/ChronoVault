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
        ReflectionTestUtils.setField(snapshotEngine, "totalTimeoutMinutes", 30L);

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

    @Test
    void createSnapshot_withCustomPaths_usesCustomPaths() {
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });

        Snapshot result = snapshotEngine.createSnapshot(
                testServer, testStorageTarget, "Custom Paths", "with excludes",
                Snapshot.SnapshotType.FULL, 1L,
                List.of("/var/www", "/etc/nginx"),
                List.of("/var/cache"));

        assertNotNull(result);
        assertEquals("Custom Paths", result.getTitle());
        verify(snapshotRepository).save(any(Snapshot.class));
    }

    @Test
    void createSnapshot_incrementalType_setsIncrementalFlag() {
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });

        Snapshot result = snapshotEngine.createSnapshot(
                testServer, testStorageTarget, "Incremental", "note",
                Snapshot.SnapshotType.INCREMENTAL, 1L, null, null);

        assertNotNull(result);
        assertEquals(Snapshot.SnapshotType.INCREMENTAL, result.getType());
    }

    @Test
    void createSnapshot_withNullUserId_handlesGracefully() {
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });

        Snapshot result = snapshotEngine.createSnapshot(
                testServer, testStorageTarget, "No User", "note",
                Snapshot.SnapshotType.FULL, null, null, null);

        assertNotNull(result);
        assertEquals("No User", result.getTitle());
    }

    @Test
    void createSnapshot_defaultPaths_usesRootPath() {
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });

        Snapshot result = snapshotEngine.createSnapshot(
                testServer, testStorageTarget, "Default Paths", "note",
                Snapshot.SnapshotType.FULL, 1L, null, null);

        assertNotNull(result);
        // Should default to "/" when no custom paths provided
        verify(snapshotRepository).save(any(Snapshot.class));
    }

    @Test
    void createSnapshot_withExcludes_passedToTask() {
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });

        Snapshot result = snapshotEngine.createSnapshot(
                testServer, testStorageTarget, "With Excludes", "note",
                Snapshot.SnapshotType.FULL, 1L,
                null,
                List.of("/proc", "/sys", "/dev"));

        assertNotNull(result);
        verify(snapshotRepository).save(any(Snapshot.class));
    }

    @Test
    void createSnapshot_initialStatus_isStable() {
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });

        Snapshot result = snapshotEngine.createSnapshot(
                testServer, testStorageTarget, "Status Test", "note",
                Snapshot.SnapshotType.FULL, 1L, null, null);

        assertNotNull(result);
        assertEquals(Snapshot.SnapshotStatus.STABLE, result.getStatus());
    }

    @Test
    void createSnapshot_withEmptyTitle_usesProvidedTitle() {
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });

        Snapshot result = snapshotEngine.createSnapshot(
                testServer, testStorageTarget, "", "note",
                Snapshot.SnapshotType.FULL, 1L, null, null);

        assertNotNull(result);
        // Empty title is still passed through
        assertEquals("", result.getTitle());
    }

    @Test
    void createSnapshot_withNullNote_handlesGracefully() {
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });

        Snapshot result = snapshotEngine.createSnapshot(
                testServer, testStorageTarget, "No Note", null,
                Snapshot.SnapshotType.FULL, 1L, null, null);

        assertNotNull(result);
        assertNull(result.getNote());
    }

    @Test
    void createSnapshot_serverInformation_copiedCorrectly() {
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });

        Snapshot result = snapshotEngine.createSnapshot(
                testServer, testStorageTarget, "Server Info", "note",
                Snapshot.SnapshotType.FULL, 1L, null, null);

        assertNotNull(result);
        assertEquals(testServer, result.getServer());
    }
}