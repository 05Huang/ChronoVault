package com.chronovault.service;

import com.chronovault.dto.server.CloneServerRequest;
import com.chronovault.entity.Server;
import com.chronovault.entity.StorageTarget;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.snapshot.SnapshotEngine;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.task.AsyncTaskManager;
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
class ServerCloneServiceTest {

    @Mock private ServerRepository serverRepository;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private SnapshotEngine snapshotEngine;
    @Mock private SshConnectionManager sshManager;
    @Mock private ResticClient resticClient;
    @Mock private AsyncTaskManager taskManager;
    @Mock private ServerService serverService;

    @InjectMocks
    private ServerCloneService service;

    private Server testServer;
    private StorageTarget testTarget;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "resticPassword", "test-password");
        testServer = Server.builder().id(1L).name("source-server").ip("10.0.0.1").build();
        testTarget = StorageTarget.builder().id(1L).name("local")
                .type(StorageTarget.StorageType.LOCAL).endpoint("/data").build();
    }

    @Test
    void cloneServer_sourceNotFound_throwsException() {
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        CloneServerRequest request = new CloneServerRequest(999L, "10.0.0.2", "clone-server", 22, "root");
        assertThrows(ResourceNotFoundException.class, () -> service.cloneServer(request, 1L));
    }

    @Test
    void cloneServer_targetIpExists_throwsException() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(serverRepository.existsByIp("10.0.0.2")).thenReturn(true);
        CloneServerRequest request = new CloneServerRequest(1L, "10.0.0.2", "clone-server", 22, "root");
        assertThrows(BadRequestException.class, () -> service.cloneServer(request, 1L));
    }

    @Test
    void cloneServer_noStorageTargets_throwsException() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(serverRepository.existsByIp("10.0.0.2")).thenReturn(false);
        when(storageTargetRepository.findAll()).thenReturn(List.of());
        CloneServerRequest request = new CloneServerRequest(1L, "10.0.0.2", "clone-server", 22, "root");
        assertThrows(BadRequestException.class, () -> service.cloneServer(request, 1L));
    }
}