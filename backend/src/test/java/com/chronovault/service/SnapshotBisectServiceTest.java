package com.chronovault.service;

import com.chronovault.dto.snapshot.BisectMarkRequest;
import com.chronovault.dto.snapshot.BisectSessionDTO;
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

    @Test
    void start_withEnoughSnapshots_createsSession() {
        // Create 5 snapshots: snap1(good) snap2 snap3 snap4 snap5(bad)
        Snapshot good = Snapshot.builder().id(1L).server(testServer).title("snap1")
                .createdAt(LocalDateTime.now().minusDays(10)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s2 = Snapshot.builder().id(2L).server(testServer).title("snap2")
                .createdAt(LocalDateTime.now().minusDays(8)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s3 = Snapshot.builder().id(3L).server(testServer).title("snap3")
                .createdAt(LocalDateTime.now().minusDays(6)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s4 = Snapshot.builder().id(4L).server(testServer).title("snap4")
                .createdAt(LocalDateTime.now().minusDays(4)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot bad = Snapshot.builder().id(5L).server(testServer).title("snap5")
                .createdAt(LocalDateTime.now().minusDays(2)).status(Snapshot.SnapshotStatus.STABLE).build();

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(good));
        when(snapshotRepository.findById(5L)).thenReturn(Optional.of(bad));
        // findByServerIdOrderByCreatedAtDesc returns newest first
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(bad, s4, s3, s2, good));
        when(tagRepository.findBySnapshotIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());

        BisectStartRequest request = new BisectStartRequest(1L, 1L, 5L);
        BisectSessionDTO session = service.start(request);

        assertEquals("IN_PROGRESS", session.status());
        assertEquals(3, session.totalSteps()); // ceil(log2(5)) = 3
        assertNotNull(session.sessionId());
    }

    @Test
    void mark_goodVerdict_narrowsTowardsBadEnd() {
        // Setup: start a session with 5 snapshots
        Snapshot good = Snapshot.builder().id(1L).server(testServer).title("snap1")
                .createdAt(LocalDateTime.now().minusDays(10)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s2 = Snapshot.builder().id(2L).server(testServer).title("snap2")
                .createdAt(LocalDateTime.now().minusDays(8)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s3 = Snapshot.builder().id(3L).server(testServer).title("snap3")
                .createdAt(LocalDateTime.now().minusDays(6)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s4 = Snapshot.builder().id(4L).server(testServer).title("snap4")
                .createdAt(LocalDateTime.now().minusDays(4)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot bad = Snapshot.builder().id(5L).server(testServer).title("snap5")
                .createdAt(LocalDateTime.now().minusDays(2)).status(Snapshot.SnapshotStatus.STABLE).build();

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(good));
        when(snapshotRepository.findById(5L)).thenReturn(Optional.of(bad));
        when(snapshotRepository.findById(3L)).thenReturn(Optional.of(s3));
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(bad, s4, s3, s2, good));
        when(tagRepository.findBySnapshotIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());

        BisectStartRequest startReq = new BisectStartRequest(1L, 1L, 5L);
        BisectSessionDTO session = service.start(startReq);

        // Mark the midpoint (snap3, id=3) as good → should narrow to [snap4, snap5]
        BisectMarkRequest markReq = new BisectMarkRequest(3L, "good");
        BisectSessionDTO afterMark = service.mark(session.sessionId(), markReq);

        // After marking snap3 as good, the new candidates should be [snap4, snap5]
        // With 2 candidates, mid = 1, so current = snap5 (id=5)
        assertEquals(5L, afterMark.currentSnapshotId());
        assertEquals("IN_PROGRESS", afterMark.status());
    }

    @Test
    void mark_badVerdict_narrowsTowardsGoodEnd() {
        // Setup: start a session with 5 snapshots
        Snapshot good = Snapshot.builder().id(1L).server(testServer).title("snap1")
                .createdAt(LocalDateTime.now().minusDays(10)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s2 = Snapshot.builder().id(2L).server(testServer).title("snap2")
                .createdAt(LocalDateTime.now().minusDays(8)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s3 = Snapshot.builder().id(3L).server(testServer).title("snap3")
                .createdAt(LocalDateTime.now().minusDays(6)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s4 = Snapshot.builder().id(4L).server(testServer).title("snap4")
                .createdAt(LocalDateTime.now().minusDays(4)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot bad = Snapshot.builder().id(5L).server(testServer).title("snap5")
                .createdAt(LocalDateTime.now().minusDays(2)).status(Snapshot.SnapshotStatus.STABLE).build();

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(good));
        when(snapshotRepository.findById(5L)).thenReturn(Optional.of(bad));
        when(snapshotRepository.findById(3L)).thenReturn(Optional.of(s3));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(s2));
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(bad, s4, s3, s2, good));
        when(tagRepository.findBySnapshotIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());

        BisectStartRequest startReq = new BisectStartRequest(1L, 1L, 5L);
        BisectSessionDTO session = service.start(startReq);

        // Mark the midpoint (snap3, id=3) as bad → should narrow to [snap1, snap2, snap3]
        BisectMarkRequest markReq = new BisectMarkRequest(3L, "bad");
        BisectSessionDTO afterMark = service.mark(session.sessionId(), markReq);

        // After marking snap3 as bad, the new candidates should be [snap1, snap2, snap3]
        // and the next midpoint should be snap2 (id=2)
        assertEquals(2L, afterMark.currentSnapshotId());
        assertEquals("IN_PROGRESS", afterMark.status());
    }

    @Test
    void mark_skipVerdict_doesNotNarrow() {
        Snapshot good = Snapshot.builder().id(1L).server(testServer).title("snap1")
                .createdAt(LocalDateTime.now().minusDays(10)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s2 = Snapshot.builder().id(2L).server(testServer).title("snap2")
                .createdAt(LocalDateTime.now().minusDays(8)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s3 = Snapshot.builder().id(3L).server(testServer).title("snap3")
                .createdAt(LocalDateTime.now().minusDays(6)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot s4 = Snapshot.builder().id(4L).server(testServer).title("snap4")
                .createdAt(LocalDateTime.now().minusDays(4)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot bad = Snapshot.builder().id(5L).server(testServer).title("snap5")
                .createdAt(LocalDateTime.now().minusDays(2)).status(Snapshot.SnapshotStatus.STABLE).build();

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(good));
        when(snapshotRepository.findById(5L)).thenReturn(Optional.of(bad));
        when(snapshotRepository.findById(3L)).thenReturn(Optional.of(s3));
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(bad, s4, s3, s2, good));
        when(tagRepository.findBySnapshotIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());

        BisectStartRequest startReq = new BisectStartRequest(1L, 1L, 5L);
        BisectSessionDTO session = service.start(startReq);

        // Mark as skip → should not narrow, just pick a different midpoint
        BisectMarkRequest markReq = new BisectMarkRequest(3L, "skip");
        BisectSessionDTO afterMark = service.mark(session.sessionId(), markReq);

        // Candidates should still be all 5, and midpoint might change
        assertEquals("IN_PROGRESS", afterMark.status());
        assertEquals(1, afterMark.totalSteps() - afterMark.stepsRemaining()); // 1 step completed
    }

    @Test
    void mark_invalidVerdict_throwsException() {
        Snapshot good = Snapshot.builder().id(1L).server(testServer).title("snap1")
                .createdAt(LocalDateTime.now().minusDays(10)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot bad = Snapshot.builder().id(2L).server(testServer).title("snap2")
                .createdAt(LocalDateTime.now().minusDays(1)).status(Snapshot.SnapshotStatus.STABLE).build();
        Snapshot mid = Snapshot.builder().id(3L).server(testServer).title("snap3")
                .createdAt(LocalDateTime.now().minusDays(5)).status(Snapshot.SnapshotStatus.STABLE).build();

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(good));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(bad));
        when(snapshotRepository.findById(3L)).thenReturn(Optional.of(mid));
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(bad, mid, good));
        when(tagRepository.findBySnapshotIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());

        BisectStartRequest startReq = new BisectStartRequest(1L, 1L, 2L);
        BisectSessionDTO session = service.start(startReq);

        BisectMarkRequest markReq = new BisectMarkRequest(3L, "invalid");
        assertThrows(BadRequestException.class, () -> service.mark(session.sessionId(), markReq));
    }

    @Test
    void getSession_nonexistent_throwsException() {
        assertThrows(ResourceNotFoundException.class, () -> service.getSession("nonexistent"));
    }
}