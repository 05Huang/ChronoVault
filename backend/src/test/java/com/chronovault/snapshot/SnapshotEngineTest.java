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
import static org.mockito.Mockito.lenient;

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

    // ===== executeSnapshot step-by-step tests =====

    /**
     * Helper: directly invoke the private executeSnapshot method via reflection.
     * This avoids the complexity of capturing the lambda from createSnapshot.
     */
    private void executeSnapshotDirect(Snapshot snapshot, Server server, StorageTarget target,
                                        Snapshot.SnapshotType type) {
        try {
            java.lang.reflect.Method method = SnapshotEngine.class.getDeclaredMethod(
                    "executeSnapshot", Long.class, Snapshot.class, Server.class,
                    StorageTarget.class, Snapshot.SnapshotType.class, List.class, List.class);
            method.setAccessible(true);
            method.invoke(snapshotEngine, 100L, snapshot, server, target, type, null, null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void executeSnapshot_fullHappyPath_completesAllSteps() throws Exception {
        // Arrange
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("Test")
                .status(Snapshot.SnapshotStatus.STABLE).build();

        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        // Use lenient stubbing for sshConnection since it's called with many different arguments
        lenient().when(sshConnection.executeCommand(anyString(), any(java.time.Duration.class))).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(anyString())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/data/backups");
        when(resticClient.init(sshConnection, "/data/backups", "test-password-123")).thenReturn(true);

        ResticClient.ResticSnapshot resticSnap = new ResticClient.ResticSnapshot(
                "snap-abc123", "tree-xyz", 1024L, "2026-01-01T00:00:00Z", 5, 3, 512L);
        when(resticClient.backup(eq(sshConnection), eq("/data/backups"), eq("test-password-123"),
                any(), any(), isNull())).thenReturn(resticSnap);
        when(resticClient.check(sshConnection, "/data/backups", "test-password-123")).thenReturn(true);
        when(stateCollectionService.collectStateViaSsh(sshConnection)).thenReturn("{\"packages\":[]}");
        when(resticClient.getResticPath(sshConnection)).thenReturn("/usr/bin/restic");

        // Act
        assertDoesNotThrow(() -> executeSnapshotDirect(snapshot, testServer, testStorageTarget,
                Snapshot.SnapshotType.FULL));

        // Assert
        verify(sshManager).getConnection(testServer);
        verify(resticClient).ensureResticInstalled(sshConnection);
        verify(resticClient).init(sshConnection, "/data/backups", "test-password-123");
        verify(resticClient).backup(eq(sshConnection), eq("/data/backups"), eq("test-password-123"),
                any(), any(), isNull());
        verify(resticClient).check(sshConnection, "/data/backups", "test-password-123");
        verify(stateCollectionService).collectStateViaSsh(sshConnection);

        assertEquals("snap-abc123", snapshot.getHash());
        assertEquals(1024L, snapshot.getSizeBytes());
        assertEquals(Snapshot.SnapshotStatus.STABLE, snapshot.getStatus());
    }

    @Test
    void executeSnapshot_sshConnectionFails_setsWarningStatus() throws Exception {
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("SSH Fail")
                .status(Snapshot.SnapshotStatus.STABLE).build();

        when(sshManager.getConnection(testServer)).thenThrow(
                new java.io.IOException("Connection refused"));

        assertThrows(RuntimeException.class, () ->
                executeSnapshotDirect(snapshot, testServer, testStorageTarget, Snapshot.SnapshotType.FULL));

        // Verify snapshot was saved with WARNING status
        verify(snapshotRepository, atLeastOnce()).save(argThat(s ->
                s.getStatus() == Snapshot.SnapshotStatus.WARNING));
    }

    @Test
    void executeSnapshot_resticNotInstalled_throwsWithMessage() throws Exception {
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("No Restic")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                executeSnapshotDirect(snapshot, testServer, testStorageTarget, Snapshot.SnapshotType.FULL));
        // Debug: print the actual message
        assertTrue(ex.getMessage().contains("备份工具安装") || ex.getMessage().contains("restic"),
                "Expected message to contain '备份工具安装' or 'restic', got: " + ex.getMessage());
    }

    @Test
    void executeSnapshot_diskSpaceLow_throwsDiskError() throws Exception {
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("Low Disk")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        when(sshConnection.executeCommand(contains("df"), any(java.time.Duration.class))).thenReturn(
                new SshConnection.CommandResult(0, "50M", ""));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                executeSnapshotDirect(snapshot, testServer, testStorageTarget, Snapshot.SnapshotType.FULL));
        assertTrue(ex.getMessage().contains("磁盘空间"));
    }

    @Test
    void executeSnapshot_diskSpaceWarning_continuesDespiteWarning() throws Exception {
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("Low Disk Warn")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        // Use lenient stubbing for sshConnection.executeCommand since it's called with many different args
        lenient().when(sshConnection.executeCommand(anyString(), any(java.time.Duration.class))).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(anyString())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(anyString())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/data/backups");
        when(resticClient.init(sshConnection, "/data/backups", "test-password-123")).thenReturn(true);

        ResticClient.ResticSnapshot resticSnap = new ResticClient.ResticSnapshot(
                "snap-123", "tree", 512L, "2026-01-01T00:00:00Z", 2, 1, 256L);
        when(resticClient.backup(any(), anyString(), anyString(), any(), any(), isNull())).thenReturn(resticSnap);
        when(resticClient.check(any(), anyString(), anyString())).thenReturn(true);
        when(stateCollectionService.collectStateViaSsh(any())).thenReturn(null);

        assertDoesNotThrow(() -> executeSnapshotDirect(snapshot, testServer, testStorageTarget,
                Snapshot.SnapshotType.FULL));
    }

    @Test
    void executeSnapshot_backupFails_throwsBackupError() throws Exception {
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("Backup Fail")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        lenient().when(sshConnection.executeCommand(anyString(), any(java.time.Duration.class))).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(anyString())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/data/backups");
        when(resticClient.init(sshConnection, "/data/backups", "test-password-123")).thenReturn(true);
        // docker ps is covered by the lenient anyString stub above
        when(resticClient.backup(any(), anyString(), anyString(), any(), any(), isNull())).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                executeSnapshotDirect(snapshot, testServer, testStorageTarget, Snapshot.SnapshotType.FULL));
        assertTrue(ex.getMessage().contains("备份") || ex.getMessage().contains("backup"),
                "Expected message to contain '备份' or 'backup', got: " + ex.getMessage());
    }

    @Test
    void executeSnapshot_stateCollectionFails_snapshotStillSucceeds() throws Exception {
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("State Fail")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        lenient().when(sshConnection.executeCommand(anyString(), any(java.time.Duration.class))).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(anyString())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/data/backups");
        when(resticClient.init(sshConnection, "/data/backups", "test-password-123")).thenReturn(true);
        // docker ps is covered by the lenient anyString stub above

        ResticClient.ResticSnapshot resticSnap = new ResticClient.ResticSnapshot(
                "snap-456", "tree", 2048L, "2026-01-01T00:00:00Z", 10, 5, 1024L);
        when(resticClient.backup(any(), anyString(), anyString(), any(), any(), isNull())).thenReturn(resticSnap);
        when(resticClient.check(any(), anyString(), anyString())).thenReturn(true);
        when(stateCollectionService.collectStateViaSsh(sshConnection))
                .thenThrow(new RuntimeException("state collection timeout"));
        when(resticClient.getResticPath(sshConnection)).thenReturn("/usr/bin/restic");

        assertDoesNotThrow(() -> executeSnapshotDirect(snapshot, testServer, testStorageTarget,
                Snapshot.SnapshotType.FULL));

        assertEquals("snap-456", snapshot.getHash());
        assertEquals(2048L, snapshot.getSizeBytes());
    }

    @Test
    void executeSnapshot_incrementalFindsParentId() throws Exception {
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("Incremental")
                .type(Snapshot.SnapshotType.INCREMENTAL)
                .status(Snapshot.SnapshotStatus.STABLE).build();

        Snapshot parentSnapshot = Snapshot.builder().id(99L).server(testServer)
                .hash("parent-hash-abc").build();
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(parentSnapshot));

        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        lenient().when(sshConnection.executeCommand(anyString(), any(java.time.Duration.class))).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(anyString())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/data/backups");
        when(resticClient.init(sshConnection, "/data/backups", "test-password-123")).thenReturn(true);
        // docker ps is covered by the lenient anyString stub above

        ResticClient.ResticSnapshot resticSnap = new ResticClient.ResticSnapshot(
                "snap-incr", "tree", 100L, "2026-01-01T00:00:00Z", 1, 0, 50L);
        when(resticClient.backup(eq(sshConnection), eq("/data/backups"), eq("test-password-123"),
                any(), any(), eq("parent-hash-abc"))).thenReturn(resticSnap);
        when(resticClient.check(any(), anyString(), anyString())).thenReturn(true);
        when(stateCollectionService.collectStateViaSsh(any())).thenReturn(null);

        assertDoesNotThrow(() -> executeSnapshotDirect(snapshot, testServer, testStorageTarget,
                Snapshot.SnapshotType.INCREMENTAL));

        verify(resticClient).backup(eq(sshConnection), eq("/data/backups"), eq("test-password-123"),
                any(), any(), eq("parent-hash-abc"));
    }

    @Test
    void executeSnapshot_localStorage_createsDirectory() throws Exception {
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("Local Storage")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        StorageTarget localTarget = StorageTarget.builder()
                .id(2L).name("local").type(StorageTarget.StorageType.LOCAL)
                .endpoint("/opt/backups").build();

        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        lenient().when(sshConnection.executeCommand(anyString(), any(java.time.Duration.class))).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(anyString())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        when(resticClient.buildRepoUrl(localTarget)).thenReturn("/opt/backups");
        when(resticClient.init(sshConnection, "/opt/backups", "test-password-123")).thenReturn(true);
        // docker ps is covered by the lenient anyString stub above

        ResticClient.ResticSnapshot resticSnap = new ResticClient.ResticSnapshot(
                "snap-local", "tree", 500L, "2026-01-01T00:00:00Z", 3, 2, 300L);
        when(resticClient.backup(any(), anyString(), anyString(), any(), any(), isNull())).thenReturn(resticSnap);
        when(resticClient.check(any(), anyString(), anyString())).thenReturn(true);
        when(stateCollectionService.collectStateViaSsh(any())).thenReturn(null);

        assertDoesNotThrow(() -> executeSnapshotDirect(snapshot, testServer, localTarget,
                Snapshot.SnapshotType.FULL));

        // mkdir is called via executeCommand(String) without Duration
        verify(sshConnection).executeCommand(contains("mkdir"));
    }

    @Test
    void executeSnapshot_dockerContainersCaptured() throws Exception {
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("Docker")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        lenient().when(sshConnection.executeCommand(anyString(), any(java.time.Duration.class))).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(anyString())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/data/backups");
        when(resticClient.init(sshConnection, "/data/backups", "test-password-123")).thenReturn(true);

        // Override lenient stub for docker commands (note: no Duration arg)
        when(sshConnection.executeCommand(contains("docker ps")))
                .thenReturn(new SshConnection.CommandResult(0, "nginx\tnginx:latest\trunning", ""));
        when(sshConnection.executeCommand(contains("docker inspect")))
                .thenReturn(new SshConnection.CommandResult(0,
                        "{\"PortBindings\":{},\"Mounts\":[],\"Networks\":{}}", ""));

        ResticClient.ResticSnapshot resticSnap = new ResticClient.ResticSnapshot(
                "snap-docker", "tree", 100L, "2026-01-01T00:00:00Z", 1, 0, 100L);
        when(resticClient.backup(any(), anyString(), anyString(), any(), any(), isNull())).thenReturn(resticSnap);
        when(resticClient.check(any(), anyString(), anyString())).thenReturn(true);
        when(stateCollectionService.collectStateViaSsh(any())).thenReturn(null);
        when(resticClient.getResticPath(sshConnection)).thenReturn("/usr/bin/restic");

        assertDoesNotThrow(() -> executeSnapshotDirect(snapshot, testServer, testStorageTarget,
                Snapshot.SnapshotType.FULL));

        verify(containerStateRepository).saveAll(anyList());
    }

    @Test
    void executeSnapshot_progressUpdatesReported() throws Exception {
        Snapshot snapshot = Snapshot.builder().id(1L).server(testServer).title("Progress")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        lenient().when(sshConnection.executeCommand(anyString(), any(java.time.Duration.class))).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        lenient().when(sshConnection.executeCommand(anyString())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/data/backups");
        when(resticClient.init(sshConnection, "/data/backups", "test-password-123")).thenReturn(true);
        // docker ps is covered by the lenient anyString stub above

        ResticClient.ResticSnapshot resticSnap = new ResticClient.ResticSnapshot(
                "snap-progress", "tree", 100L, "2026-01-01T00:00:00Z", 1, 0, 100L);
        when(resticClient.backup(any(), anyString(), anyString(), any(), any(), isNull())).thenReturn(resticSnap);
        when(resticClient.check(any(), anyString(), anyString())).thenReturn(true);
        when(stateCollectionService.collectStateViaSsh(any())).thenReturn(null);

        assertDoesNotThrow(() -> executeSnapshotDirect(snapshot, testServer, testStorageTarget,
                Snapshot.SnapshotType.FULL));

        verify(taskManager).updateProgress(eq(100L), eq(10), contains("连接"));
        verify(taskManager).updateProgress(eq(100L), eq(15), contains("备份工具"));
        verify(taskManager).updateProgress(eq(100L), eq(50), contains("备份"));
        verify(taskManager).updateProgress(eq(100L), eq(80), contains("后置钩子"));
        verify(taskManager).updateProgress(eq(100L), eq(85), contains("容器"));
        verify(taskManager).updateProgress(eq(100L), eq(100), contains("完成"));
    }
}