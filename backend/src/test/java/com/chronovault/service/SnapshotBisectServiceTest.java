package com.chronovault.service;

import com.chronovault.dto.snapshot.BisectStartRequest;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotBisectServiceTest {

    @Mock private SnapshotRepository snapshotRepository;
    @Mock private SnapshotTagRepository tagRepository;

    @InjectMocks
    private SnapshotBisectService service;

    private Server testServer;

    @BeforeEach
    void setUp() {
        testServer = Server.builder().id(1L).name("test-server").build();
    }

    @Test
    void start_goodSnapshotNotFound_throwsException() {
        when(snapshotRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        BisectStartRequest request = new BisectStartRequest(1L, 999L, 2L);
        assertThrows(ResourceNotFoundException.class, () -> service.start(request));
    }

    @Test
    void start_badSnapshotNotFound_throwsException() {
        Snapshot good = Snapshot.builder().id(1L).server(testServer)
                .createdAt(LocalDateTime.now().minusDays(5))
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(good));
        when(snapshotRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        BisectStartRequest request = new BisectStartRequest(1L, 1L, 999L);
        assertThrows(ResourceNotFoundException.class, () -> service.start(request));
    }

    @Test
    void start_differentServers_throwsException() {
        Server otherServer = Server.builder().id(2L).name("other-server").build();
        Snapshot good = Snapshot.builder().id(1L).server(testServer)
                .createdAt(LocalDateTime.now().minusDays(5))
                .status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot bad = Snapshot.builder().id(2L).server(otherServer)
                .createdAt(LocalDateTime.now().minusDays(1))
                .status(Snapshot.SnapshotStatus.STABLE).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(good));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(bad));
        BisectStartRequest request = new BisectStartRequest(1L, 1L, 2L);
        assertThrows(BadRequestException.class, () -> service.start(request));
    }
}