package com.chronovault.service;

import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.task.AsyncTaskManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageReplicationServiceTest {

    @Mock private SnapshotRepository snapshotRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private SshConnectionManager sshManager;
    @Mock private ResticClient resticClient;
    @Mock private AsyncTaskManager taskManager;

    @InjectMocks
    private StorageReplicationService service;

    @Test
    void replicateSnapshot_snapshotNotFound_throwsException() {
        when(snapshotRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.replicateSnapshot(999L, 1L));
    }

    @Test
    void replicateSnapshot_noHash_throwsException() {
        Snapshot snapshot = Snapshot.builder().id(1L).hash(null).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapshot));
        assertThrows(BadRequestException.class, () -> service.replicateSnapshot(1L, 1L));
    }

    @Test
    void replicateSnapshot_targetNotFound_throwsException() {
        Snapshot snapshot = Snapshot.builder().id(1L).hash("abc123").build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapshot));
        when(storageTargetRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.replicateSnapshot(1L, 999L));
    }
}