package com.chronovault.service;

import com.chronovault.dto.snapshot.CreateSnapshotRequest;
import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.snapshot.SnapshotDiffDTO;
import com.chronovault.entity.*;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.*;
import com.chronovault.diff.StateDiffEngine;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.snapshot.SnapshotEngine;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.*;

@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {

    @Mock private SnapshotRepository snapshotRepository;
    @Mock private SnapshotDiffRepository snapshotDiffRepository;
    @Mock private SnapshotTagRepository tagRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private UserRepository userRepository;
    @Mock private SnapshotEngine snapshotEngine;
    @Mock private SshConnectionManager sshManager;
    @Mock private ResticClient resticClient;
    @Mock private ChangeAttributionService attributionService;
    @Mock private StateDiffEngine stateDiffEngine;
    @Mock private com.chronovault.metrics.BackupMetrics backupMetrics;
    @Mock private com.chronovault.repository.AlertRepository alertRepository;
    @Mock private NotificationService notificationService;
    @Mock private SshConnection sshConnection;

    @InjectMocks
    private SnapshotService snapshotService;

    private Server testServer;
    private Snapshot testSnapshot;
    private User testUser;
    private StorageTarget testStorageTarget;

    private static final StateDiffEngine REAL_DIFF_ENGINE = new StateDiffEngine(new com.fasterxml.jackson.databind.ObjectMapper());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(snapshotService, "resticPassword", "test-password");
        testUser = User.builder().id(1L).name("Test User").email("test@example.com").build();
        testServer = Server.builder().id(1L).user(testUser).name("Test Server").ip("192.168.1.1").status(Server.ServerStatus.RUNNING).build();
        testSnapshot = Snapshot.builder().id(1L).server(testServer).title("Test Snapshot").hash("abc123").status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();
        testStorageTarget = StorageTarget.builder().id(1L).type(StorageTarget.StorageType.LOCAL).endpoint("/backup").build();
    }

    @Test
    void getSnapshots_returnsList() {
        when(snapshotRepository.findAll()).thenReturn(List.of(testSnapshot));
        when(tagRepository.findBySnapshotIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        List<SnapshotDTO> result = snapshotService.getSnapshots();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Snapshot", result.get(0).name());
    }

    @Test
    void getSnapshots_emptyList() {
        when(snapshotRepository.findAll()).thenReturn(List.of());
        List<SnapshotDTO> result = snapshotService.getSnapshots();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getSnapshot_existingId_returnsSnapshot() {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(testSnapshot));
        when(tagRepository.findBySnapshotIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        SnapshotDTO result = snapshotService.getSnapshot(1L);
        assertNotNull(result);
        assertEquals("Test Snapshot", result.name());
    }

    @Test
    void getSnapshot_nonExistingId_throwsException() {
        when(snapshotRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> snapshotService.getSnapshot(999L));
    }

    @Test
    void deleteSnapshot_existingId_deletes() {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(testSnapshot));
        snapshotService.deleteSnapshot(1L);
        verify(snapshotRepository).delete(testSnapshot);
    }

    @Test
    void deleteSnapshot_nonExistingId_throwsException() {
        when(snapshotRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> snapshotService.deleteSnapshot(999L));
    }

    @Test
    void batchDelete_deletesMultipleSnapshots() {
        Snapshot s1 = Snapshot.builder().id(1L).server(testServer).title("S1").status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s2 = Snapshot.builder().id(2L).server(testServer).title("S2").status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(s1, s2));
        int deleted = snapshotService.batchDelete(List.of(1L, 2L));
        assertEquals(2, deleted);
        verify(snapshotRepository).deleteAll(List.of(s1, s2));
    }

    @Test
    void batchDelete_emptyList_returnsZero() {
        when(snapshotRepository.findAllById(List.of())).thenReturn(List.of());
        int deleted = snapshotService.batchDelete(List.of());
        assertEquals(0, deleted);
    }

    @Test
    void createSnapshot_invalidServer_throwsException() {
        CreateSnapshotRequest request = new CreateSnapshotRequest(999L, null, "FULL", "test", null, null);
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> snapshotService.createSnapshot(request, 1L));
    }

    @Test
    void createSnapshot_noStorageTargets_throwsException() {
        CreateSnapshotRequest request = new CreateSnapshotRequest(1L, null, "FULL", "test", null, null);
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(storageTargetRepository.findAll()).thenReturn(List.of());
        assertThrows(BadRequestException.class, () -> snapshotService.createSnapshot(request, 1L));
    }

    @Test
    void getSnapshotDiff_returnsDiffList() {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(testSnapshot));
        when(snapshotRepository.findPreviousSnapshots(eq(1L), eq(1L), any())).thenReturn(List.of());
        when(snapshotDiffRepository.findBySnapshotId(1L)).thenReturn(List.of());
        List<SnapshotDiffDTO> result = snapshotService.getSnapshotDiff(1L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void verifySnapshot_nonExistingSnapshot_throwsException() {
        when(snapshotRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> snapshotService.verifySnapshot(999L));
    }

    @Test
    void verifySnapshot_noHash_throwsException() {
        Snapshot noHashSnapshot = Snapshot.builder().id(2L).server(testServer).title("No Hash").status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(noHashSnapshot));
        assertThrows(BadRequestException.class, () -> snapshotService.verifySnapshot(2L));
    }

    @Test
    void getStateSnapshot_returnsStateJson() {
        String stateJson = "{\"packages\":[],\"services\":[]}";
        Snapshot snapWithState = Snapshot.builder().id(1L).server(testServer).title("State Test")
                .stateJson(stateJson).status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithState));

        String result = snapshotService.getStateSnapshot(1L);
        assertEquals(stateJson, result);
    }

    @Test
    void getStateSnapshot_noStateJson_returnsNull() {
        Snapshot snapNoState = Snapshot.builder().id(1L).server(testServer).title("No State")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapNoState));

        String result = snapshotService.getStateSnapshot(1L);
        assertNull(result);
    }

    @Test
    void getStateSnapshot_nonExistingSnapshot_throwsException() {
        when(snapshotRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> snapshotService.getStateSnapshot(999L));
    }

    @Test
    void computeStateDiff_bothSnapshotsHaveState_computesDiff() {
        String stateA = "{\"packages\":[{\"name\":\"nginx\",\"version\":\"1.22.0\",\"manager\":\"apt\"}],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";
        String stateB = "{\"packages\":[{\"name\":\"nginx\",\"version\":\"1.24.0\",\"manager\":\"apt\"},{\"name\":\"curl\",\"version\":\"7.88.1\",\"manager\":\"apt\"}],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";

        Snapshot fromSnap = Snapshot.builder().id(1L).server(testServer).stateJson(stateA).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot toSnap = Snapshot.builder().id(2L).server(testServer).stateJson(stateB).status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(fromSnap));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(toSnap));
        doAnswer(inv -> REAL_DIFF_ENGINE.diff(inv.getArgument(0), inv.getArgument(1)))
                .when(stateDiffEngine).diff(any(), any());

        Map<String, Object> result = snapshotService.computeStateDiff(1L, 2L);

        assertNotNull(result);
        assertEquals(1L, result.get("snapshot_a"));
        assertEquals(2L, result.get("snapshot_b"));
        assertNotNull(result.get("summary"));
        assertNotNull(result.get("packages"));
    }

    @Test
    void computeStateDiff_oneSnapshotMissing_throwsException() {
        when(snapshotRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> snapshotService.computeStateDiff(1L, 2L));
    }

    @Test
    void rollbackPreview_existingSnapshot_returnsPreview() {
        Snapshot snapWithHash = Snapshot.builder().id(1L).server(testServer).title("Rollback Test")
                .hash("abc123").sizeBytes(1024000L).status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithHash));
        when(storageTargetRepository.findAll()).thenReturn(List.of(testStorageTarget));

        java.util.Map<String, Object> preview = snapshotService.rollbackPreview(1L);

        assertNotNull(preview);
        assertEquals(1L, preview.get("snapshotId"));
        assertEquals("Rollback Test", preview.get("snapshotTitle"));
        assertEquals("Test Server", preview.get("serverName"));
        assertEquals(true, preview.get("hasValidBackup"));
        assertEquals(true, preview.get("storageAvailable"));
        assertEquals("LOCAL", preview.get("storageType"));
    }

    @Test
    void rollbackPreview_noBackupData_showsInvalidBackup() {
        Snapshot snapNoHash = Snapshot.builder().id(2L).server(testServer).title("No Backup")
                .status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(snapNoHash));
        when(storageTargetRepository.findAll()).thenReturn(List.of(testStorageTarget));

        java.util.Map<String, Object> preview = snapshotService.rollbackPreview(2L);

        assertNotNull(preview);
        assertEquals(false, preview.get("hasValidBackup"));
    }

    @Test
    void rollbackPreview_nonExistingSnapshot_throwsException() {
        when(snapshotRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> snapshotService.rollbackPreview(999L));
    }

    @Test
    void detectAndAlertHighRiskChanges_highRiskPort_createsAlert() {
        String stateA = "{\"packages\":[],\"services\":[],\"ports\":[{\"port\":80,\"protocol\":\"tcp\",\"process\":\"nginx\",\"state\":\"LISTEN\"}],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";
        String stateB = "{\"packages\":[],\"services\":[],\"ports\":[{\"port\":80,\"protocol\":\"tcp\",\"process\":\"nginx\",\"state\":\"LISTEN\"},{\"port\":3306,\"protocol\":\"tcp\",\"process\":\"mysql\",\"state\":\"LISTEN\"}],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";

        Snapshot prev = Snapshot.builder().id(1L).server(testServer).title("Prev")
                .stateJson(stateA).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot curr = Snapshot.builder().id(2L).server(testServer).title("Curr")
                .stateJson(stateB).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();

        StateDiffEngine.StateDiffResult mockResult = new StateDiffEngine.StateDiffResult(
                new StateDiffEngine.PackageDiff(),
                new StateDiffEngine.ServiceDiff(),
                new StateDiffEngine.PortDiff(),
                new StateDiffEngine.DockerDiff(),
                new StateDiffEngine.ConfigDiff(),
                new StateDiffEngine.CrontabDiff(),
                new StateDiffEngine.DiffSummary()
        );
        mockResult.ports().added.add("3306/tcp");

        when(stateDiffEngine.diff(stateA, stateB)).thenReturn(mockResult);

        snapshotService.detectAndAlertHighRiskChanges(prev, curr);

        verify(alertRepository).save(argThat(alert -> {
            Alert a = (Alert) alert;
            return a.getTitle().contains("高风险变更")
                    && a.getDescription().contains("3306")
                    && a.getSource().equals("snapshot-diff");
        }));
    }

    @Test
    void detectAndAlertHighRiskChanges_serviceDisabled_createsAlert() {
        String stateA = "{\"packages\":[],\"services\":[{\"name\":\"nginx\",\"status\":\"active\",\"enabled\":true}],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";
        String stateB = "{\"packages\":[],\"services\":[{\"name\":\"nginx\",\"status\":\"inactive\",\"enabled\":false}],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";

        Snapshot prev = Snapshot.builder().id(1L).server(testServer).title("Prev")
                .stateJson(stateA).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot curr = Snapshot.builder().id(2L).server(testServer).title("Curr")
                .stateJson(stateB).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();

        StateDiffEngine.StateDiffResult mockResult = new StateDiffEngine.StateDiffResult(
                new StateDiffEngine.PackageDiff(),
                new StateDiffEngine.ServiceDiff(),
                new StateDiffEngine.PortDiff(),
                new StateDiffEngine.DockerDiff(),
                new StateDiffEngine.ConfigDiff(),
                new StateDiffEngine.CrontabDiff(),
                new StateDiffEngine.DiffSummary()
        );
        StateDiffEngine.ServiceChange change = new StateDiffEngine.ServiceChange("nginx");
        change.fromEnabled = true;
        change.toEnabled = false;
        change.fromStatus = "active";
        change.toStatus = "inactive";
        mockResult.services().changed.add(change);

        when(stateDiffEngine.diff(stateA, stateB)).thenReturn(mockResult);

        snapshotService.detectAndAlertHighRiskChanges(prev, curr);

        verify(alertRepository).save(argThat(alert -> {
            Alert a = (Alert) alert;
            return a.getTitle().contains("高风险变更")
                    && a.getDescription().contains("nginx");
        }));
    }

    @Test
    void detectAndAlertHighRiskChanges_noHighRisk_noAlert() {
        String stateA = "{\"packages\":[{\"name\":\"curl\",\"version\":\"7.88\",\"manager\":\"apt\"}],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";
        String stateB = "{\"packages\":[{\"name\":\"curl\",\"version\":\"7.88\",\"manager\":\"apt\"},{\"name\":\"wget\",\"version\":\"1.21\",\"manager\":\"apt\"}],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";

        Snapshot prev = Snapshot.builder().id(1L).server(testServer).title("Prev")
                .stateJson(stateA).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot curr = Snapshot.builder().id(2L).server(testServer).title("Curr")
                .stateJson(stateB).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();

        StateDiffEngine.StateDiffResult mockResult = new StateDiffEngine.StateDiffResult(
                new StateDiffEngine.PackageDiff(),
                new StateDiffEngine.ServiceDiff(),
                new StateDiffEngine.PortDiff(),
                new StateDiffEngine.DockerDiff(),
                new StateDiffEngine.ConfigDiff(),
                new StateDiffEngine.CrontabDiff(),
                new StateDiffEngine.DiffSummary()
        );
        mockResult.packages().added.add(new StateDiffEngine.PackageInfo("wget", "1.21"));

        when(stateDiffEngine.diff(stateA, stateB)).thenReturn(mockResult);

        snapshotService.detectAndAlertHighRiskChanges(prev, curr);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void detectAndAlertHighRiskChanges_nullStateJson_doesNothing() {
        Snapshot prev = Snapshot.builder().id(1L).server(testServer).title("Prev")
                .status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot curr = Snapshot.builder().id(2L).server(testServer).title("Curr")
                .status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();

        snapshotService.detectAndAlertHighRiskChanges(prev, curr);

        verify(stateDiffEngine, never()).diff(any(), any());
        verify(alertRepository, never()).save(any());
    }

    @Test
    void rollback_existingSnapshotWithHash_callsRestore() throws Exception {
        Snapshot snapWithHash = Snapshot.builder().id(1L).server(testServer).title("Rollback Test")
                .hash("abc123").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithHash));
        when(storageTargetRepository.findAll()).thenReturn(List.of(testStorageTarget));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/backup");
        when(resticClient.restore(sshConnection, "/backup", "test-password", "abc123", "/")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        snapshotService.rollback(1L, 1L);

        verify(resticClient).restore(sshConnection, "/backup", "test-password", "abc123", "/");
        verify(snapshotRepository).save(argThat(s -> s.getStatus() == Snapshot.SnapshotStatus.STABLE));
    }

    @Test
    void rollback_restoreFails_setsWarningStatus() throws Exception {
        Snapshot snapWithHash = Snapshot.builder().id(1L).server(testServer).title("Rollback Test")
                .hash("abc123").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithHash));
        when(storageTargetRepository.findAll()).thenReturn(List.of(testStorageTarget));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/backup");
        when(resticClient.restore(sshConnection, "/backup", "test-password", "abc123", "/")).thenReturn(false);

        snapshotService.rollback(1L, 1L);

        verify(snapshotRepository).save(argThat(s -> s.getStatus() == Snapshot.SnapshotStatus.WARNING));
    }

    @Test
    void rollback_noStorageTargets_throwsException() {
        Snapshot snapWithHash = Snapshot.builder().id(1L).server(testServer).title("Rollback Test")
                .hash("abc123").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithHash));
        when(storageTargetRepository.findAll()).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> snapshotService.rollback(1L, 1L));
    }

    @Test
    void selectiveRollback_configType_dumpsAndWrites() throws Exception {
        Snapshot snapWithHash = Snapshot.builder().id(1L).server(testServer).title("Selective Rollback")
                .hash("abc123").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithHash));
        when(storageTargetRepository.findAll()).thenReturn(List.of(testStorageTarget));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/backup");
        when(resticClient.dumpFile(sshConnection, "/backup", "test-password", "abc123", "/etc/nginx/nginx.conf"))
                .thenReturn("server { listen 80; }");
        when(sshConnection.executeCommand(any(String.class), any())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        java.util.List<Map<String, String>> items = List.of(
                Map.of("type", "config", "path", "/etc/nginx/nginx.conf")
        );

        String result = snapshotService.selectiveRollback(1L, items, 1L);

        assertTrue(result.contains("成功恢复 1/1"));
        verify(resticClient).dumpFile(sshConnection, "/backup", "test-password", "abc123", "/etc/nginx/nginx.conf");
    }

    @Test
    void selectiveRollback_packageType_installsPackage() throws Exception {
        Snapshot snapWithHash = Snapshot.builder().id(1L).server(testServer).title("Selective Rollback")
                .hash("abc123").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithHash));
        when(storageTargetRepository.findAll()).thenReturn(List.of(testStorageTarget));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        when(sshConnection.executeCommand(any(String.class), any())).thenReturn(
                new SshConnection.CommandResult(0, "", ""));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        java.util.List<Map<String, String>> items = List.of(
                Map.of("type", "package", "name", "nginx", "target_version", "1.22.0")
        );

        String result = snapshotService.selectiveRollback(1L, items, 1L);

        assertTrue(result.contains("成功恢复 1/1"));
        verify(sshConnection).executeCommand(argThat(cmd -> cmd.contains("nginx") && cmd.contains("1.22.0")), any());
    }

    @Test
    void selectiveRollback_emptyItems_throwsException() {
        Snapshot snapWithHash = Snapshot.builder().id(1L).server(testServer).title("Selective Rollback")
                .hash("abc123").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithHash));

        assertThrows(BadRequestException.class, () ->
                snapshotService.selectiveRollback(1L, List.of(), 1L));
    }

    @Test
    void selectiveRollback_noHash_throwsException() {
        Snapshot snapNoHash = Snapshot.builder().id(1L).server(testServer).title("No Hash")
                .status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapNoHash));

        java.util.List<Map<String, String>> items = List.of(
                Map.of("type", "config", "path", "/etc/nginx/nginx.conf")
        );

        assertThrows(BadRequestException.class, () ->
                snapshotService.selectiveRollback(1L, items, 1L));
    }

    @Test
    void getChangeSummary_returnsSummary() {
        String summaryJson = "{\"packages_added\":1,\"services_changed\":0}";
        Snapshot snapWithSummary = Snapshot.builder().id(1L).server(testServer).title("Summary Test")
                .changeSummaryJson(summaryJson).status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithSummary));

        String result = snapshotService.getChangeSummary(1L);
        assertEquals(summaryJson, result);
    }

    @Test
    void getChangeSummary_noSummary_returnsNull() {
        Snapshot snapNoSummary = Snapshot.builder().id(1L).server(testServer).title("No Summary")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapNoSummary));

        String result = snapshotService.getChangeSummary(1L);
        assertNull(result);
    }

    @Test
    void getSnapshotsForTimeline_returnsPaginatedResults() {
        Snapshot s1 = Snapshot.builder().id(1L).server(testServer).title("S1").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now().minusDays(2)).build();
        Snapshot s2 = Snapshot.builder().id(2L).server(testServer).title("S2").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot s3 = Snapshot.builder().id(3L).server(testServer).title("S3").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();

        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(s3, s2, s1));
        when(tagRepository.findBySnapshotIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());

        List<SnapshotDTO> result = snapshotService.getSnapshotsForTimeline(1L, 0, 2);

        assertEquals(2, result.size());
        assertEquals("S3", result.get(0).name());
        assertEquals("S2", result.get(1).name());
    }

    @Test
    void computeStateDiff_verifiesDiffResultStructure() {
        String stateA = "{\"packages\":[{\"name\":\"nginx\",\"version\":\"1.22.0\",\"manager\":\"apt\"}],\"services\":[{\"name\":\"nginx\",\"status\":\"active\",\"enabled\":true}],\"ports\":[{\"port\":80,\"protocol\":\"tcp\"}],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[{\"path\":\"/etc/nginx.conf\",\"sha256\":\"aaa\"}],\"crontab\":[]}";
        String stateB = "{\"packages\":[{\"name\":\"nginx\",\"version\":\"1.24.0\",\"manager\":\"apt\"},{\"name\":\"curl\",\"version\":\"7.88.1\",\"manager\":\"apt\"}],\"services\":[{\"name\":\"nginx\",\"status\":\"active\",\"enabled\":true}],\"ports\":[{\"port\":80,\"protocol\":\"tcp\"},{\"port\":443,\"protocol\":\"tcp\"}],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[{\"path\":\"/etc/nginx.conf\",\"sha256\":\"bbb\"}],\"crontab\":[]}";

        Snapshot fromSnap = Snapshot.builder().id(1L).server(testServer).stateJson(stateA).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot toSnap = Snapshot.builder().id(2L).server(testServer).stateJson(stateB).status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(fromSnap));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(toSnap));
        doAnswer(inv -> REAL_DIFF_ENGINE.diff(inv.getArgument(0), inv.getArgument(1)))
                .when(stateDiffEngine).diff(any(), any());

        Map<String, Object> result = snapshotService.computeStateDiff(1L, 2L);

        assertNotNull(result);
        // Verify all sections are present
        assertNotNull(result.get("summary"));
        assertNotNull(result.get("packages"));
        assertNotNull(result.get("services"));
        assertNotNull(result.get("ports"));
        assertNotNull(result.get("docker"));
        assertNotNull(result.get("configs"));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertEquals(1, summary.get("packages_added"));   // curl
        assertEquals(0, summary.get("packages_removed")); // no packages removed in this test
        assertEquals(1, summary.get("packages_upgraded")); // nginx 1.22 -> 1.24
        assertEquals(1, summary.get("ports_changed"));    // 443 added
        assertEquals(1, summary.get("configs_changed"));  // nginx.conf changed
    }

    @Test
    void computeStateDiff_bothSnapshotsNullState_returnsEmptyDiff() {
        Snapshot fromSnap = Snapshot.builder().id(1L).server(testServer).stateJson(null).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot toSnap = Snapshot.builder().id(2L).server(testServer).stateJson(null).status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(fromSnap));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(toSnap));

        Map<String, Object> result = snapshotService.computeStateDiff(1L, 2L);

        assertNotNull(result);
        assertEquals(1L, result.get("snapshot_a"));
        assertEquals(2L, result.get("snapshot_b"));
        // With null inputs, StateDiffEngine returns empty result
        assertNotNull(result.get("summary"));
    }

    @Test
    void detectAndAlertHighRiskChanges_multipleRisks_createsSingleAlertWithAllReasons() {
        String stateA = "{\"packages\":[],\"services\":[{\"name\":\"sshd\",\"status\":\"active\",\"enabled\":true}],\"ports\":[{\"port\":80,\"protocol\":\"tcp\"}],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[{\"path\":\"/etc/hosts\",\"sha256\":\"aaa\"}],\"crontab\":[]}";
        String stateB = "{\"packages\":[],\"services\":[{\"name\":\"sshd\",\"status\":\"inactive\",\"enabled\":false}],\"ports\":[{\"port\":80,\"protocol\":\"tcp\"},{\"port\":22,\"protocol\":\"tcp\"}],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[{\"path\":\"/etc/hosts\",\"sha256\":\"bbb\"}],\"crontab\":[]}";

        Snapshot prev = Snapshot.builder().id(1L).server(testServer).title("Prev")
                .stateJson(stateA).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot curr = Snapshot.builder().id(2L).server(testServer).title("Curr")
                .stateJson(stateB).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();
        curr.setServer(testServer);

        StateDiffEngine.StateDiffResult mockResult = new StateDiffEngine.StateDiffResult(
                new StateDiffEngine.PackageDiff(),
                new StateDiffEngine.ServiceDiff(),
                new StateDiffEngine.PortDiff(),
                new StateDiffEngine.DockerDiff(),
                new StateDiffEngine.ConfigDiff(),
                new StateDiffEngine.CrontabDiff(),
                new StateDiffEngine.DiffSummary()
        );
        mockResult.ports().added.add("22/tcp");
        StateDiffEngine.ServiceChange change = new StateDiffEngine.ServiceChange("sshd");
        change.fromEnabled = true;
        change.toEnabled = false;
        change.fromStatus = "active";
        change.toStatus = "inactive";
        mockResult.services().changed.add(change);
        mockResult.configs().changed.add("/etc/hosts");

        when(stateDiffEngine.diff(stateA, stateB)).thenReturn(mockResult);

        snapshotService.detectAndAlertHighRiskChanges(prev, curr);

        // Should create ONE alert with ALL risk reasons combined
        verify(alertRepository, times(1)).save(argThat(alert -> {
            Alert a = (Alert) alert;
            return a.getTitle().contains("3") && // 3 reasons
                    a.getDescription().contains("22") &&
                    a.getDescription().contains("sshd") &&
                    a.getDescription().contains("/etc/hosts");
        }));
    }

    @Test
    void detectAndAlertHighRiskChanges_onlyWarnsOnDisabled_notEnabled() {
        String stateA = "{\"packages\":[],\"services\":[{\"name\":\"nginx\",\"status\":\"active\",\"enabled\":false}],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";
        String stateB = "{\"packages\":[],\"services\":[{\"name\":\"nginx\",\"status\":\"active\",\"enabled\":true}],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";

        Snapshot prev = Snapshot.builder().id(1L).server(testServer).title("Prev")
                .stateJson(stateA).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot curr = Snapshot.builder().id(2L).server(testServer).title("Curr")
                .stateJson(stateB).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();
        curr.setServer(testServer);

        StateDiffEngine.StateDiffResult mockResult = new StateDiffEngine.StateDiffResult(
                new StateDiffEngine.PackageDiff(),
                new StateDiffEngine.ServiceDiff(),
                new StateDiffEngine.PortDiff(),
                new StateDiffEngine.DockerDiff(),
                new StateDiffEngine.ConfigDiff(),
                new StateDiffEngine.CrontabDiff(),
                new StateDiffEngine.DiffSummary()
        );
        StateDiffEngine.ServiceChange change = new StateDiffEngine.ServiceChange("nginx");
        change.fromEnabled = false;
        change.toEnabled = true;
        change.fromStatus = "active";
        change.toStatus = "active";
        mockResult.services().changed.add(change);

        when(stateDiffEngine.diff(stateA, stateB)).thenReturn(mockResult);

        snapshotService.detectAndAlertHighRiskChanges(prev, curr);

        // Enabling a service is NOT a risk - should NOT create alert
        verify(alertRepository, never()).save(any());
    }

    @Test
    void detectAndAlertHighRiskChanges_sudoersChange_createsAlert() {
        String stateA = "{\"packages\":[],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[{\"path\":\"/etc/sudoers\",\"sha256\":\"aaa\",\"size\":500}],\"crontab\":[]}";
        String stateB = "{\"packages\":[],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[{\"path\":\"/etc/sudoers\",\"sha256\":\"bbb\",\"size\":500}],\"crontab\":[]}";

        Snapshot prev = Snapshot.builder().id(1L).server(testServer).title("Prev")
                .stateJson(stateA).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot curr = Snapshot.builder().id(2L).server(testServer).title("Curr")
                .stateJson(stateB).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();
        curr.setServer(testServer);

        StateDiffEngine.StateDiffResult mockResult = new StateDiffEngine.StateDiffResult(
                new StateDiffEngine.PackageDiff(),
                new StateDiffEngine.ServiceDiff(),
                new StateDiffEngine.PortDiff(),
                new StateDiffEngine.DockerDiff(),
                new StateDiffEngine.ConfigDiff(),
                new StateDiffEngine.CrontabDiff(),
                new StateDiffEngine.DiffSummary()
        );
        mockResult.configs().changed.add("/etc/sudoers");

        when(stateDiffEngine.diff(stateA, stateB)).thenReturn(mockResult);

        snapshotService.detectAndAlertHighRiskChanges(prev, curr);

        verify(alertRepository).save(argThat(alert -> {
            Alert a = (Alert) alert;
            return a.getTitle().contains("高风险变更")
                    && a.getDescription().contains("/etc/sudoers");
        }));
    }

    @Test
    void detectAndAlertHighRiskChanges_sshdConfigChange_createsAlert() {
        String stateA = "{\"packages\":[],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[{\"path\":\"/etc/ssh/sshd_config\",\"sha256\":\"aaa\",\"size\":1000}],\"crontab\":[]}";
        String stateB = "{\"packages\":[],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[{\"path\":\"/etc/ssh/sshd_config\",\"sha256\":\"bbb\",\"size\":1000}],\"crontab\":[]}";

        Snapshot prev = Snapshot.builder().id(1L).server(testServer).title("Prev")
                .stateJson(stateA).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot curr = Snapshot.builder().id(2L).server(testServer).title("Curr")
                .stateJson(stateB).status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();
        curr.setServer(testServer);

        StateDiffEngine.StateDiffResult mockResult = new StateDiffEngine.StateDiffResult(
                new StateDiffEngine.PackageDiff(),
                new StateDiffEngine.ServiceDiff(),
                new StateDiffEngine.PortDiff(),
                new StateDiffEngine.DockerDiff(),
                new StateDiffEngine.ConfigDiff(),
                new StateDiffEngine.CrontabDiff(),
                new StateDiffEngine.DiffSummary()
        );
        mockResult.configs().changed.add("/etc/ssh/sshd_config");

        when(stateDiffEngine.diff(stateA, stateB)).thenReturn(mockResult);

        snapshotService.detectAndAlertHighRiskChanges(prev, curr);

        verify(alertRepository).save(argThat(alert -> {
            Alert a = (Alert) alert;
            return a.getTitle().contains("高风险变更")
                    && a.getDescription().contains("/etc/ssh/sshd_config");
        }));
    }

    // =====================================================================
    // P1-1: End-to-End Snapshot Lifecycle Tests
    // =====================================================================

    @Test
    void e2e_snapshotLifecycle_createSnapshotWithStateJson() {
        // Setup: server exists with storage target
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(storageTargetRepository.findById(1L)).thenReturn(Optional.of(testStorageTarget));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Mock SnapshotEngine to return a snapshot with stateJson
        Snapshot createdSnapshot = Snapshot.builder().id(10L).server(testServer).title("E2E Test")
                .status(Snapshot.SnapshotStatus.STABLE).build();
        createdSnapshot.setStateJson("{\"packages\":[{\"name\":\"nginx\",\"version\":\"1.24.0\"}],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}");
        createdSnapshot.setHash("abc123");
        when(snapshotEngine.createSnapshot(any(), any(), any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(createdSnapshot);

        // Step 1: Create snapshot via service
        CreateSnapshotRequest request = new CreateSnapshotRequest(1L, 1L, "FULL", "E2E test snapshot", null, null);
        SnapshotDTO result = snapshotService.createSnapshot(request, 1L);

        // Verify: snapshot created with state_json
        assertNotNull(result);
        assertEquals(10L, result.id());

        // Verify: SnapshotEngine was invoked
        verify(snapshotEngine).createSnapshot(eq(testServer), eq(testStorageTarget),
                anyString(), eq("E2E test snapshot"), eq(Snapshot.SnapshotType.FULL),
                eq(1L), isNull(), isNull());
    }

    @Test
    void e2e_snapshotLifecycle_twoSnapshots_produceDiff() {
        // Setup two snapshots with state.json
        String stateA = "{\"packages\":[{\"name\":\"nginx\",\"version\":\"1.22.0\",\"manager\":\"apt\"}],\"services\":[{\"name\":\"nginx\",\"status\":\"active\",\"enabled\":true}],\"ports\":[{\"port\":80,\"protocol\":\"tcp\",\"process\":\"nginx\",\"state\":\"LISTEN\"}],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[{\"path\":\"/etc/nginx/nginx.conf\",\"sha256\":\"aaa\",\"size\":1024}],\"crontab\":[]}";
        String stateB = "{\"packages\":[{\"name\":\"nginx\",\"version\":\"1.24.0\",\"manager\":\"apt\"},{\"name\":\"curl\",\"version\":\"7.88.1\",\"manager\":\"apt\"}],\"services\":[{\"name\":\"nginx\",\"status\":\"active\",\"enabled\":true},{\"name\":\"redis\",\"status\":\"active\",\"enabled\":true}],\"ports\":[{\"port\":80,\"protocol\":\"tcp\",\"process\":\"nginx\",\"state\":\"LISTEN\"},{\"port\":6379,\"protocol\":\"tcp\",\"process\":\"redis\",\"state\":\"LISTEN\"}],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[{\"path\":\"/etc/nginx/nginx.conf\",\"sha256\":\"bbb\",\"size\":1024}],\"crontab\":[]}";

        Snapshot snap1 = Snapshot.builder().id(1L).server(testServer).title("Before")
                .stateJson(stateA).hash("hash1").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot snap2 = Snapshot.builder().id(2L).server(testServer).title("After")
                .stateJson(stateB).hash("hash2").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snap1));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(snap2));
        doAnswer(inv -> REAL_DIFF_ENGINE.diff(inv.getArgument(0), inv.getArgument(1)))
                .when(stateDiffEngine).diff(any(), any());

        // Compute state diff
        Map<String, Object> diffResult = snapshotService.computeStateDiff(1L, 2L);

        // Verify diff structure
        assertNotNull(diffResult);
        assertEquals(1L, diffResult.get("snapshot_a"));
        assertEquals(2L, diffResult.get("snapshot_b"));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) diffResult.get("summary");
        assertNotNull(summary);
        assertEquals(1, summary.get("packages_added"));    // curl
        assertEquals(1, summary.get("packages_upgraded")); // nginx 1.22→1.24
        assertEquals(1, summary.get("services_changed"));  // redis added
        assertEquals(1, summary.get("ports_changed"));     // 6379 added
        assertEquals(1, summary.get("configs_changed"));   // nginx.conf changed

        @SuppressWarnings("unchecked")
        Map<String, Object> packages = (Map<String, Object>) diffResult.get("packages");
        assertNotNull(packages);
        assertNotNull(packages.get("added"));
        assertNotNull(packages.get("upgraded"));
    }

    @Test
    void e2e_snapshotLifecycle_rollbackPreview_showsCorrectInfo() {
        Snapshot snapWithHash = Snapshot.builder().id(1L).server(testServer).title("Rollback E2E")
                .hash("abc123def").sizeBytes(2048000L).status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now().minusHours(2))
                .changeSummaryJson("{\"packages_added\":1,\"configs_changed\":2}")
                .build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithHash));
        when(storageTargetRepository.findAll()).thenReturn(List.of(testStorageTarget));

        // Get rollback preview
        Map<String, Object> preview = snapshotService.rollbackPreview(1L);

        // Verify preview contains all expected fields
        assertNotNull(preview);
        assertEquals(1L, preview.get("snapshotId"));
        assertEquals("Rollback E2E", preview.get("snapshotTitle"));
        assertEquals("Test Server", preview.get("serverName"));
        assertEquals(true, preview.get("hasValidBackup"));
        assertEquals(true, preview.get("storageAvailable"));
        assertEquals("LOCAL", preview.get("storageType"));
        assertNotNull(preview.get("estimatedRestoreTimeSeconds"));
        assertNotNull(preview.get("changeSummary"));
    }

    @Test
    void e2e_snapshotLifecycle_selectiveRollback_configAndPackage() throws Exception {
        Snapshot snapWithHash = Snapshot.builder().id(1L).server(testServer).title("Selective E2E")
                .hash("abc123").status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapWithHash));
        when(storageTargetRepository.findAll()).thenReturn(List.of(testStorageTarget));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(resticClient.ensureResticInstalled(sshConnection)).thenReturn(true);
        when(resticClient.buildRepoUrl(testStorageTarget)).thenReturn("/backup");
        when(resticClient.dumpFile(sshConnection, "/backup", "test-password", "abc123", "/etc/nginx/nginx.conf"))
                .thenReturn("server { listen 80; }");
        when(sshConnection.executeCommand(any(String.class), any()))
                .thenReturn(new SshConnection.CommandResult(0, "", ""));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Execute selective rollback with mixed item types
        java.util.List<Map<String, String>> items = List.of(
                Map.of("type", "config", "path", "/etc/nginx/nginx.conf"),
                Map.of("type", "package", "name", "curl", "target_version", "7.88.1")
        );

        String result = snapshotService.selectiveRollback(1L, items, 1L);

        // Verify result
        assertNotNull(result);
        assertTrue(result.contains("成功恢复"));
        assertTrue(result.contains("/etc/nginx/nginx.conf"));
        assertTrue(result.contains("curl"));
    }

    @Test
    void e2e_snapshotLifecycle_changeSummary_computedAndCached() {
        // Two snapshots with state.json for the same server
        String stateA = "{\"packages\":[{\"name\":\"nginx\",\"version\":\"1.22.0\",\"manager\":\"apt\"}],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";
        String stateB = "{\"packages\":[{\"name\":\"nginx\",\"version\":\"1.24.0\",\"manager\":\"apt\"},{\"name\":\"git\",\"version\":\"2.42.0\",\"manager\":\"apt\"}],\"services\":[],\"ports\":[],\"docker\":{\"containers\":[],\"compose_files\":[]},\"configs\":[],\"crontab\":[]}";

        Snapshot prev = Snapshot.builder().id(1L).server(testServer).title("Prev")
                .stateJson(stateA).status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now().minusDays(1)).build();
        Snapshot curr = Snapshot.builder().id(2L).server(testServer).title("Curr")
                .stateJson(stateB).status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now()).build();

        // Mock: computeAndCacheChangeSummary finds the previous snapshot
        when(snapshotRepository.findByServerIdAndCreatedAtBeforeOrderByCreatedAtAsc(1L, curr.getCreatedAt()))
                .thenReturn(List.of(prev));

        // Mock: stateDiffEngine returns a real diff
        StateDiffEngine.StateDiffResult mockResult = new StateDiffEngine.StateDiffResult(
                new StateDiffEngine.PackageDiff(),
                new StateDiffEngine.ServiceDiff(),
                new StateDiffEngine.PortDiff(),
                new StateDiffEngine.DockerDiff(),
                new StateDiffEngine.ConfigDiff(),
                new StateDiffEngine.CrontabDiff(),
                new StateDiffEngine.DiffSummary()
        );
        mockResult.packages().added.add(new StateDiffEngine.PackageInfo("git", "2.42.0"));
        mockResult.packages().upgraded.add(new StateDiffEngine.PackageUpgrade("nginx", "1.22.0", "1.24.0"));
        when(stateDiffEngine.diff(stateA, stateB)).thenReturn(mockResult);

        // Execute
        snapshotService.computeAndCacheChangeSummary(curr);

        // Verify: changeSummaryJson was set and saved
        verify(snapshotRepository).save(argThat(s -> {
            Snapshot saved = (Snapshot) s;
            return saved.getChangeSummaryJson() != null
                    && saved.getChangeSummaryJson().contains("packages_added")
                    && saved.getPreviousSnapshot() != null;
        }));
    }
}
