package com.chronovault.service;

import com.chronovault.config.DistributedLock;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoSnapshotServiceTest {

    @Mock private ServerRepository serverRepository;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private SnapshotEngine snapshotEngine;
    @Mock private SshConnectionManager sshManager;
    @Mock private ResticClient resticClient;
    @Mock private DistributedLock distributedLock;

    @InjectMocks
    private AutoSnapshotService service;

    private Server testServer;
    private StorageTarget testTarget;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "resticPassword", "test-password");
        testServer = Server.builder().id(1L).name("test-server").ip("10.0.0.1")
                .status(Server.ServerStatus.RUNNING).autoSnapshotEnabled(true).build();
        testTarget = StorageTarget.builder().id(1L).name("local")
                .type(StorageTarget.StorageType.LOCAL).endpoint("/data").build();
    }

    @Test
    void checkAndAutoSnapshot_lockNotAcquired_skips() {
        when(distributedLock.tryLock(anyString(), any())).thenReturn(null);
        service.checkAndAutoSnapshot();
        verify(serverRepository, never()).findByAutoSnapshotEnabledTrueAndStatus(any());
    }

    @Test
    void checkAndAutoSnapshot_noServers_doesNothing() {
        when(distributedLock.tryLock(anyString(), any())).thenReturn("lock-123");
        when(serverRepository.findByAutoSnapshotEnabledTrueAndStatus(any())).thenReturn(List.of());

        service.checkAndAutoSnapshot();

        verify(distributedLock).releaseLock(eq("auto-snapshot"), eq("lock-123"));
    }

    @Test
    void setAutoSnapshotEnabled_togglesCorrectly() {
        when(serverRepository.findById(1L)).thenReturn(java.util.Optional.of(testServer));

        service.setAutoSnapshotEnabled(1L, false);
        assertFalse(testServer.isAutoSnapshotEnabled());
        verify(serverRepository).save(testServer);
    }

    @Test
    void setAutoSnapshotEnabled_serverNotFound_throws() {
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        assertThrows(com.chronovault.exception.ResourceNotFoundException.class,
                () -> service.setAutoSnapshotEnabled(999L, true));
    }
}