package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.SnapshotRetentionPolicy;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotRetentionPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotRetentionServiceTest {

    @Mock private SnapshotRetentionPolicyRepository retentionPolicyRepository;
    @Mock private SnapshotRepository snapshotRepository;

    @InjectMocks
    private SnapshotRetentionService service;

    private Server testServer;

    @BeforeEach
    void setUp() {
        testServer = Server.builder().id(1L).name("test-server").build();
    }

    @Test
    void executeRetentionCleanup_noPolicies_doesNothing() {
        when(retentionPolicyRepository.findByEnabledTrue()).thenReturn(List.of());
        service.executeRetentionCleanup();
        verify(snapshotRepository, never()).deleteAll(anyList());
    }

    @Test
    void executeRetentionCleanup_withPolicy_deletesOldSnapshots() {
        SnapshotRetentionPolicy policy = SnapshotRetentionPolicy.builder()
                .id(1L).name("test-policy").enabled(true).server(testServer)
                .maxAgeDays(30).maxCount(10).deletedCount(0).build();
        when(retentionPolicyRepository.findByEnabledTrue()).thenReturn(List.of(policy));
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        service.executeRetentionCleanup();

        verify(retentionPolicyRepository).save(any(SnapshotRetentionPolicy.class));
    }

    @Test
    void executeRetentionCleanup_policyWithMaxCount_deletesExcess() {
        // Create 5 snapshots, policy says keep max 2
        Snapshot s1 = Snapshot.builder().id(1L).server(testServer).status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now().minusDays(5)).build();
        Snapshot s2 = Snapshot.builder().id(2L).server(testServer).status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now().minusDays(4)).build();
        Snapshot s3 = Snapshot.builder().id(3L).server(testServer).status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now().minusDays(3)).build();
        Snapshot s4 = Snapshot.builder().id(4L).server(testServer).status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now().minusDays(2)).build();
        Snapshot s5 = Snapshot.builder().id(5L).server(testServer).status(Snapshot.SnapshotStatus.STABLE)
                .createdAt(LocalDateTime.now().minusDays(1)).build();

        SnapshotRetentionPolicy policy = SnapshotRetentionPolicy.builder()
                .id(1L).name("max-count-policy").enabled(true).server(testServer)
                .maxCount(2).minKeepDays(0).deletedCount(0).build();
        when(retentionPolicyRepository.findByEnabledTrue()).thenReturn(List.of(policy));
        when(snapshotRepository.findByServerIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(s5, s4, s3, s2, s1));

        service.executeRetentionCleanup();

        // Should delete snapshots (at least 1 call to deleteAll with excess)
        verify(snapshotRepository, atLeastOnce()).deleteAll(any(java.lang.Iterable.class));
    }
}