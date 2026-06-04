package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotTagRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.snapshot.SnapshotEngine;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotStashServiceTest {

    @Mock private SnapshotRepository snapshotRepository;
    @Mock private SnapshotTagRepository tagRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private SnapshotEngine snapshotEngine;
    @Mock private SshConnectionManager sshManager;
    @Mock private ResticClient resticClient;

    @InjectMocks
    private SnapshotStashService service;

    private Server testServer;
    private StorageTarget testTarget;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "resticPassword", "test-password");
        testServer = Server.builder().id(1L).name("test-server").ip("10.0.0.1")
                .status(Server.ServerStatus.RUNNING).build();
        testTarget = StorageTarget.builder().id(1L).name("local")
                .type(StorageTarget.StorageType.LOCAL).endpoint("/data").build();
    }

    @Test
    void createStash_noStorageTarget_throwsException() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(storageTargetRepository.findAll()).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> service.createStash(1L, "test", 1L));
    }

    @Test
    void createStash_serverNotFound_throwsException() {
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createStash(999L, "test", 1L));
    }

    @Test
    void listStashes_filtersByType() {
        Snapshot stash = Snapshot.builder().id(1L).server(testServer).type(Snapshot.SnapshotType.STASH)
                .status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();
        Snapshot regular = Snapshot.builder().id(2L).server(testServer).type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(stash, regular));
        when(tagRepository.findBySnapshotIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());

        var result = service.listStashes(1L);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
    }

    @Test
    void popStash_noStashes_throwsException() {
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        assertThrows(BadRequestException.class, () -> service.popStash(1L, 1L));
    }

    @Test
    void popStash_noStorageTarget_throwsException() {
        Snapshot stash = Snapshot.builder().id(1L).server(testServer).type(Snapshot.SnapshotType.STASH)
                .hash("abc123").createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(stash));
        when(storageTargetRepository.findAll()).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> service.popStash(1L, 1L));
    }
}