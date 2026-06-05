package com.chronovault.integration;

import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.SnapshotTag;
import com.chronovault.entity.User;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.SnapshotTagRepository;
import com.chronovault.repository.UserRepository;
import com.chronovault.service.SnapshotService;
import com.chronovault.service.SnapshotTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for batch operations:
 * - Batch delete snapshots
 * - Bulk tag snapshots
 * - Multi-server batch snapshot (DB-level only, no actual Restic)
 */
@Transactional
class BatchOperationsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ServerRepository serverRepository;
    @Autowired private SnapshotRepository snapshotRepository;
    @Autowired private SnapshotTagRepository snapshotTagRepository;
    @Autowired private SnapshotService snapshotService;
    @Autowired private SnapshotTagService snapshotTagService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("Batch Test User")
                .email("batch-test-" + System.nanoTime() + "@test.com")
                .passwordHash("hashed")
                .role(User.Role.MEMBER)
                .status(User.UserStatus.ONLINE)
                .build());
    }

    // ===== Batch Delete Tests =====

    @Test
    void batchDelete_multipleSnapshots_succeeds() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("batch-server").ip("10.0.0.1")
                .status(Server.ServerStatus.RUNNING).build());

        java.util.ArrayList<Snapshot> snapshots = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            snapshots.add(snapshotRepository.save(Snapshot.builder()
                    .server(server).title("Snapshot " + i)
                    .type(Snapshot.SnapshotType.FULL).status(Snapshot.SnapshotStatus.STABLE).build()));
        }

        List<Long> ids = snapshots.stream().map(Snapshot::getId).toList();
        assertEquals(5, snapshotRepository.findAllById(ids).size());

        int deleted = snapshotService.batchDelete(ids.subList(0, 3));
        assertEquals(3, deleted);

        assertEquals(2, snapshotRepository.findByServerIdOrderByCreatedAtDesc(server.getId()).size());
    }

    @Test
    void batchDelete_partialIds_succeeds() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("partial-server").ip("10.0.0.2")
                .status(Server.ServerStatus.RUNNING).build());

        Snapshot s1 = snapshotRepository.save(Snapshot.builder()
                .server(server).title("S1").type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).build());
        Snapshot s2 = snapshotRepository.save(Snapshot.builder()
                .server(server).title("S2").type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).build());

        int deleted = snapshotService.batchDelete(List.of(s1.getId(), 99999L));
        assertEquals(1, deleted);
        assertTrue(snapshotRepository.existsById(s2.getId()));
        assertFalse(snapshotRepository.existsById(s1.getId()));
    }

    @Test
    void batchDelete_emptyList_returnsZero() {
        int deleted = snapshotService.batchDelete(List.of());
        assertEquals(0, deleted);
    }

    // ===== Bulk Tag Tests =====

    @Test
    void bulkTag_multipleSnapshots_succeeds() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("tag-server").ip("10.0.0.3")
                .status(Server.ServerStatus.RUNNING).build());

        Snapshot s1 = snapshotRepository.save(Snapshot.builder()
                .server(server).title("S1").type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).build());
        Snapshot s2 = snapshotRepository.save(Snapshot.builder()
                .server(server).title("S2").type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).build());
        Snapshot s3 = snapshotRepository.save(Snapshot.builder()
                .server(server).title("S3").type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).build());

        int count = snapshotTagService.bulkTag(
                Arrays.asList(s1.getId(), s2.getId(), s3.getId()),
                "production", "#FF0000", testUser.getId());

        assertEquals(3, count);

        List<SnapshotTag> tags = snapshotTagRepository.findBySnapshotIdOrderByCreatedAtDesc(s1.getId());
        assertFalse(tags.isEmpty());
        assertEquals("production", tags.get(0).getName());
    }

    @Test
    void bulkTag_skipsDuplicateTag_succeeds() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("dup-tag-server").ip("10.0.0.4")
                .status(Server.ServerStatus.RUNNING).build());

        Snapshot s1 = snapshotRepository.save(Snapshot.builder()
                .server(server).title("S1").type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).build());
        Snapshot s2 = snapshotRepository.save(Snapshot.builder()
                .server(server).title("S2").type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).build());

        snapshotTagService.bulkTag(List.of(s1.getId()), "staging", "#00FF00", testUser.getId());

        int count = snapshotTagService.bulkTag(
                Arrays.asList(s1.getId(), s2.getId()), "staging", "#00FF00", testUser.getId());

        assertEquals(1, count);

        List<SnapshotTag> tags = snapshotTagRepository.findBySnapshotIdOrderByCreatedAtDesc(s1.getId());
        assertEquals(1, tags.size());
    }

    @Test
    void bulkTag_differentTags_coexist() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("multi-tag-server").ip("10.0.0.5")
                .status(Server.ServerStatus.RUNNING).build());

        Snapshot s1 = snapshotRepository.save(Snapshot.builder()
                .server(server).title("S1").type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).build());

        snapshotTagService.bulkTag(List.of(s1.getId()), "production", "#FF0000", testUser.getId());
        snapshotTagService.bulkTag(List.of(s1.getId()), "critical", "#00FF00", testUser.getId());

        List<SnapshotTag> tags = snapshotTagRepository.findBySnapshotIdOrderByCreatedAtDesc(s1.getId());
        assertEquals(2, tags.size());

        var tagNames = tags.stream().map(SnapshotTag::getName).toList();
        assertTrue(tagNames.contains("production"));
        assertTrue(tagNames.contains("critical"));
    }

    @Test
    void bulkTag_nonExistentSnapshot_skipped() {
        int count = snapshotTagService.bulkTag(
                Arrays.asList(99999L, 99998L), "test", "#000000", testUser.getId());
        assertEquals(0, count);
    }

    // ===== Multi-Server Batch Snapshot (DB-level) =====

    @Test
    void multiServerSnapshot_createsSnapshotsOnMultipleServers() {
        Server server1 = serverRepository.save(Server.builder()
                .user(testUser).name("server-1").ip("10.0.0.1")
                .status(Server.ServerStatus.RUNNING).build());
        Server server2 = serverRepository.save(Server.builder()
                .user(testUser).name("server-2").ip("10.0.0.2")
                .status(Server.ServerStatus.RUNNING).build());

        Snapshot snap1 = snapshotRepository.save(Snapshot.builder()
                .server(server1).title("Batch - server-1").note("Batch #test123")
                .type(Snapshot.SnapshotType.FULL).status(Snapshot.SnapshotStatus.STABLE).build());
        Snapshot snap2 = snapshotRepository.save(Snapshot.builder()
                .server(server2).title("Batch - server-2").note("Batch #test123")
                .type(Snapshot.SnapshotType.FULL).status(Snapshot.SnapshotStatus.STABLE).build());

        assertNotNull(snap1.getId());
        assertNotNull(snap2.getId());
        assertEquals(server1.getId(), snap1.getServer().getId());
        assertEquals(server2.getId(), snap2.getServer().getId());

        var server1Snaps = snapshotRepository.findByServerIdOrderByCreatedAtDesc(server1.getId());
        var server2Snaps = snapshotRepository.findByServerIdOrderByCreatedAtDesc(server2.getId());
        assertEquals(1, server1Snaps.size());
        assertEquals(1, server2Snaps.size());
    }

    @Test
    void crossServerBulkTagging_works() {
        Server server1 = serverRepository.save(Server.builder()
                .user(testUser).name("cs-server-1").ip("10.0.0.10")
                .status(Server.ServerStatus.RUNNING).build());
        Server server2 = serverRepository.save(Server.builder()
                .user(testUser).name("cs-server-2").ip("10.0.0.11")
                .status(Server.ServerStatus.RUNNING).build());

        Snapshot s1 = snapshotRepository.save(Snapshot.builder()
                .server(server1).title("CS-S1").type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).build());
        Snapshot s2 = snapshotRepository.save(Snapshot.builder()
                .server(server2).title("CS-S2").type(Snapshot.SnapshotType.FULL)
                .status(Snapshot.SnapshotStatus.STABLE).build());

        int count = snapshotTagService.bulkTag(
                Arrays.asList(s1.getId(), s2.getId()), "release-v1", "#0000FF", testUser.getId());

        assertEquals(2, count);

        assertFalse(snapshotTagRepository.findBySnapshotIdOrderByCreatedAtDesc(s1.getId()).isEmpty());
        assertFalse(snapshotTagRepository.findBySnapshotIdOrderByCreatedAtDesc(s2.getId()).isEmpty());
    }
}
