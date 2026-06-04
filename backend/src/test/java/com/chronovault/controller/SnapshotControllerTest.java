package com.chronovault.controller;

import com.chronovault.dto.snapshot.*;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.service.*;
import com.chronovault.repository.ContainerStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotControllerTest {

    @Mock
    private SnapshotService snapshotService;
    @Mock
    private SnapshotTagService tagService;
    @Mock
    private UserService userService;
    @Mock
    private SnapshotBisectService bisectService;
    @Mock
    private ContainerStateRepository containerStateRepository;
    @Mock
    private StorageReplicationService replicationService;
    @Mock
    private BatchSnapshotService batchService;

    @InjectMocks
    private SnapshotController controller;

    private SnapshotDTO createTestSnapshot(Long id, String name) {
        return new SnapshotDTO(id, name, "2026-06-01T10:00:00", "STABLE", "test note",
                "abc123", 0, "test-server", 1024L, null, List.of(), null, null);
    }

    @Test
    void getSnapshot_validId_returnsSnapshot() {
        when(snapshotService.getSnapshot(1L)).thenReturn(createTestSnapshot(1L, "Test Snapshot"));

        var response = controller.getSnapshot(1L);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().data().id());
        assertEquals("Test Snapshot", response.getBody().data().name());
    }

    @Test
    void getSnapshot_nonExistent_throwsException() {
        when(snapshotService.getSnapshot(999L))
                .thenThrow(new ResourceNotFoundException("快照不存在: 999"));

        assertThrows(ResourceNotFoundException.class, () -> controller.getSnapshot(999L));
    }

    @Test
    void getSnapshots_withPagination_returnsPage() {
        var page = new org.springframework.data.domain.PageImpl<>(
                List.of(createTestSnapshot(1L, "Snap 1"), createTestSnapshot(2L, "Snap 2")),
                org.springframework.data.domain.PageRequest.of(0, 20), 2);
        when(snapshotService.getSnapshotsPaged(0, 20, "createdAt", "desc")).thenReturn(page);

        var response = controller.getSnapshots(0, 20, null, "createdAt", "desc");
        assertNotNull(response);
    }

    @Test
    void getSnapshots_byTag_returnsFilteredList() {
        when(snapshotService.getSnapshotsByTag("production"))
                .thenReturn(List.of(createTestSnapshot(1L, "Prod Snap")));

        var response = controller.getSnapshots(0, 20, "production", "createdAt", "desc");
        assertNotNull(response);
    }

    @Test
    void getAllSnapshots_returnsList() {
        when(snapshotService.getSnapshots())
                .thenReturn(List.of(createTestSnapshot(1L, "Snap 1"), createTestSnapshot(2L, "Snap 2")));

        var response = controller.getAllSnapshots();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().data().size());
    }

    @Test
    void deleteSnapshot_validId_succeeds() {
        doNothing().when(snapshotService).deleteSnapshot(1L);

        assertDoesNotThrow(() -> controller.deleteSnapshot(1L));
        verify(snapshotService).deleteSnapshot(1L);
    }

    @Test
    void batchTag_withValidData_succeeds() {
        when(userService.getByEmail("test@test.com")).thenReturn(
                com.chronovault.entity.User.builder().id(1L).email("test@test.com").build());
        when(tagService.bulkTag(anyList(), eq("production"), eq("#00ff00"), eq(1L))).thenReturn(3);

        BatchTagRequest request = new BatchTagRequest(List.of(1L, 2L, 3L), "production", "#00ff00");
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.batchTag(auth, request);
        assertEquals(200, response.getStatusCode().value());
    }

    // ===== State.json endpoint tests =====

    @Test
    void getStateSnapshot_withStateJson_returnsJson() {
        String stateJson = "{\"packages\":[],\"services\":[]}";
        when(snapshotService.getStateSnapshot(1L)).thenReturn(stateJson);

        var response = controller.getStateSnapshot(1L);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(stateJson, response.getBody().data());
    }

    @Test
    void getStateSnapshot_noStateJson_returnsNullMessage() {
        when(snapshotService.getStateSnapshot(1L)).thenReturn(null);

        var response = controller.getStateSnapshot(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getStateSnapshot_nonExistent_throwsException() {
        when(snapshotService.getStateSnapshot(999L))
                .thenThrow(new ResourceNotFoundException("快照不存在: 999"));

        assertThrows(ResourceNotFoundException.class, () -> controller.getStateSnapshot(999L));
    }

    @Test
    void getChangeSummary_withSummary_returnsJson() {
        String summary = "{\"packages_added\":1,\"services_changed\":0}";
        when(snapshotService.getChangeSummary(1L)).thenReturn(summary);

        var response = controller.getChangeSummary(1L);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(summary, response.getBody().data());
    }

    @Test
    void getChangeSummary_noSummary_returnsNull() {
        when(snapshotService.getChangeSummary(1L)).thenReturn(null);

        var response = controller.getChangeSummary(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getStateDiff_bothSnapshotsHaveState_returnsDiff() {
        var diffResult = new java.util.HashMap<String, Object>();
        diffResult.put("snapshot_a", 1L);
        diffResult.put("snapshot_b", 2L);
        diffResult.put("summary", java.util.Map.of("packages_added", 1, "packages_removed", 0));
        when(snapshotService.computeStateDiff(1L, 2L)).thenReturn(diffResult);

        var response = controller.getStateDiff(1L, 2L);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1L, response.getBody().data().get("snapshot_a"));
        assertEquals(2L, response.getBody().data().get("snapshot_b"));
    }

    @Test
    void getStateDiff_fromSnapshotNotFound_throwsException() {
        when(snapshotService.computeStateDiff(999L, 1L))
                .thenThrow(new ResourceNotFoundException("源快照不存在: 999"));

        assertThrows(ResourceNotFoundException.class, () -> controller.getStateDiff(999L, 1L));
    }

    @Test
    void getTimeline_withServerId_returnsList() {
        var snapshots = List.of(
                createTestSnapshot(3L, "Snap 3"),
                createTestSnapshot(2L, "Snap 2"));
        when(snapshotService.getSnapshotsForTimeline(1L, 0, 50, "createdAt", "desc")).thenReturn(snapshots);

        var response = controller.getTimeline(1L, 0, 50, "createdAt", "desc");
        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().data().size());
    }

    @Test
    void getTimeline_emptyServer_returnsEmptyList() {
        when(snapshotService.getSnapshotsForTimeline(1L, 0, 50, "createdAt", "desc")).thenReturn(List.of());

        var response = controller.getTimeline(1L, 0, 50, "createdAt", "desc");
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().data().isEmpty());
    }

    @Test
    void getTimeline_withCustomPagination_passesParams() {
        when(snapshotService.getSnapshotsForTimeline(1L, 2, 10, "createdAt", "desc")).thenReturn(List.of());

        var response = controller.getTimeline(1L, 2, 10, "createdAt", "desc");
        assertNotNull(response);
        verify(snapshotService).getSnapshotsForTimeline(1L, 2, 10, "createdAt", "desc");
    }

    // ===== Rollback preview test =====

    @Test
    void rollbackPreview_validSnapshot_returnsPreview() {
        var preview = new java.util.HashMap<String, Object>();
        preview.put("snapshotId", 1L);
        preview.put("hasValidBackup", true);
        preview.put("storageAvailable", true);
        when(snapshotService.rollbackPreview(1L)).thenReturn(preview);

        var response = controller.rollbackPreview(1L);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().data().get("hasValidBackup"));
    }

    @Test
    void rollbackPreview_nonExistent_throwsException() {
        when(snapshotService.rollbackPreview(999L))
                .thenThrow(new ResourceNotFoundException("快照不存在: 999"));

        assertThrows(ResourceNotFoundException.class, () -> controller.rollbackPreview(999L));
    }
}