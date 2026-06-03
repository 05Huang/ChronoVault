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
        when(snapshotService.getSnapshotsPaged(0, 20)).thenReturn(page);

        var response = controller.getSnapshots(0, 20, null);
        assertNotNull(response);
    }

    @Test
    void getSnapshots_byTag_returnsFilteredList() {
        when(snapshotService.getSnapshotsByTag("production"))
                .thenReturn(List.of(createTestSnapshot(1L, "Prod Snap")));

        var response = controller.getSnapshots(0, 20, "production");
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
}