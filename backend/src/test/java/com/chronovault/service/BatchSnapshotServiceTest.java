package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.SnapshotEngine;
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
class BatchSnapshotServiceTest {

    @Mock private ServerRepository serverRepository;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private SnapshotEngine snapshotEngine;

    @InjectMocks
    private BatchSnapshotService service;

    private Server testServer;
    private StorageTarget testTarget;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "resticPassword", "test-password");
        testServer = Server.builder().id(1L).name("server1").ip("10.0.0.1").build();
        testTarget = StorageTarget.builder().id(1L).name("local").type(StorageTarget.StorageType.LOCAL).endpoint("/data").build();
    }

    @Test
    void startBatch_emptyServerIds_throwsException() {
        assertThrows(BadRequestException.class, () -> service.startBatch(List.of(), null, "test", 1L));
    }

    @Test
    void startBatch_nullServerIds_throwsException() {
        assertThrows(BadRequestException.class, () -> service.startBatch(null, null, "test", 1L));
    }

    @Test
    void startBatch_noStorageTargets_throwsException() {
        when(storageTargetRepository.findAll()).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> service.startBatch(List.of(1L), null, "test", 1L));
    }

    @Test
    void startBatch_withStorageTarget_succeeds() {
        when(serverRepository.findAllById(List.of(1L))).thenReturn(List.of(testServer));
        when(storageTargetRepository.findById(1L)).thenReturn(Optional.of(testTarget));
        when(snapshotEngine.createSnapshot(any(), any(), anyString(), anyString(), any(), anyLong(), any(), any()))
                .thenReturn(Snapshot.builder().id(1L).status(Snapshot.SnapshotStatus.STABLE).build());

        String batchId = service.startBatch(List.of(1L), 1L, "test", 1L);
        assertNotNull(batchId);
        assertTrue(batchId.length() > 0);
    }

    @Test
    void getBatchStatus_nonExisting_throwsException() {
        assertThrows(ResourceNotFoundException.class, () -> service.getBatchStatus("nonexistent"));
    }
}