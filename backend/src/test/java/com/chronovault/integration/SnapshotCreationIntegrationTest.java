package com.chronovault.integration;

import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.entity.User;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for snapshot creation flow.
 * Tests: create user -> create server -> create storage -> create snapshot -> verify DB state.
 */
@Transactional
class SnapshotCreationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ServerRepository serverRepository;
    @Autowired private StorageTargetRepository storageTargetRepository;
    @Autowired private SnapshotRepository snapshotRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("Test User")
                .email("test-" + System.nanoTime() + "@test.com")
                .passwordHash("hashed")
                .role(User.Role.MEMBER)
                .status(User.UserStatus.ONLINE)
                .build());
    }

    @Test
    void createServer_persistsCorrectly() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("test-server").ip("192.168.1.100")
                .os("Ubuntu 22.04").status(Server.ServerStatus.RUNNING)
                .sshPort(22).sshUsername("root").sshAuthMethod("KEY").build());

        assertNotNull(server.getId());
        assertEquals("test-server", server.getName());
        assertEquals(testUser.getId(), server.getUser().getId());
        assertEquals("192.168.1.100", server.getIp());

        assertTrue(serverRepository.findById(server.getId()).isPresent());
    }

    @Test
    void createStorageTarget_persistsCorrectly() {
        StorageTarget saved = storageTargetRepository.save(StorageTarget.builder()
                .user(testUser).name("local-backup").type(StorageTarget.StorageType.LOCAL)
                .endpoint("/data/backups").build());

        assertNotNull(saved.getId());
        assertEquals("local-backup", saved.getName());
        assertEquals(StorageTarget.StorageType.LOCAL, saved.getType());
    }

    @Test
    void createSnapshot_linksWithServer() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("snap-server").ip("10.0.0.1")
                .status(Server.ServerStatus.RUNNING).build());

        Snapshot saved = snapshotRepository.save(Snapshot.builder()
                .server(server).title("Test Snapshot").note("Integration test")
                .type(Snapshot.SnapshotType.FULL).status(Snapshot.SnapshotStatus.STABLE).build());

        assertNotNull(saved.getId());
        assertEquals("Test Snapshot", saved.getTitle());
        assertEquals(server.getId(), saved.getServer().getId());
        assertEquals(Snapshot.SnapshotType.FULL, saved.getType());
    }

    @Test
    void createSnapshot_withHashAndSize() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("hash-server").ip("10.0.0.2")
                .status(Server.ServerStatus.RUNNING).build());

        Snapshot saved = snapshotRepository.save(Snapshot.builder()
                .server(server).title("Hash Snapshot")
                .type(Snapshot.SnapshotType.FULL).status(Snapshot.SnapshotStatus.STABLE)
                .hash("abc123def456").sizeBytes(1024000L).build());

        assertEquals("abc123def456", saved.getHash());
        assertEquals(1024000L, saved.getSizeBytes());
    }

    @Test
    void createSnapshot_withStateJson() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("state-server").ip("10.0.0.3")
                .status(Server.ServerStatus.RUNNING).build());

        String stateJson = "{\"packages\":[{\"name\":\"nginx\",\"version\":\"1.24.0\"}],\"services\":[]}";
        Snapshot saved = snapshotRepository.save(Snapshot.builder()
                .server(server).title("State Snapshot")
                .type(Snapshot.SnapshotType.FULL).status(Snapshot.SnapshotStatus.STABLE)
                .stateJson(stateJson).stateCollectedAt(LocalDateTime.now()).build());

        assertTrue(saved.getStateJson().contains("nginx"));
        assertNotNull(saved.getStateCollectedAt());
    }

    @Test
    void snapshotLifecycle_stableToWarningToStable() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("lifecycle-server").ip("10.0.0.4")
                .status(Server.ServerStatus.RUNNING).build());

        Snapshot snapshot = snapshotRepository.save(Snapshot.builder()
                .server(server).title("Lifecycle Test")
                .type(Snapshot.SnapshotType.FULL).status(Snapshot.SnapshotStatus.STABLE).build());

        snapshot.setStatus(Snapshot.SnapshotStatus.WARNING);
        snapshot.setNote("Backup partially failed");
        assertEquals(Snapshot.SnapshotStatus.WARNING, snapshotRepository.save(snapshot).getStatus());

        snapshot.setStatus(Snapshot.SnapshotStatus.STABLE);
        snapshot.setNote(null);
        assertEquals(Snapshot.SnapshotStatus.STABLE, snapshotRepository.save(snapshot).getStatus());
    }

    @Test
    void findSnapshotsByServer_returnsCorrectOrder() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("order-server").ip("10.0.0.5")
                .status(Server.ServerStatus.RUNNING).build());

        for (int i = 0; i < 3; i++) {
            snapshotRepository.save(Snapshot.builder()
                    .server(server).title("Snapshot " + i)
                    .type(Snapshot.SnapshotType.FULL).status(Snapshot.SnapshotStatus.STABLE).build());
        }

        var snapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(server.getId());
        assertEquals(3, snapshots.size());
        assertEquals("Snapshot 2", snapshots.get(0).getTitle());
    }

    @Test
    void deleteSnapshot_cascades() {
        Server server = serverRepository.save(Server.builder()
                .user(testUser).name("delete-server").ip("10.0.0.6")
                .status(Server.ServerStatus.RUNNING).build());

        Snapshot snapshot = snapshotRepository.save(Snapshot.builder()
                .server(server).title("To Delete")
                .type(Snapshot.SnapshotType.FULL).status(Snapshot.SnapshotStatus.STABLE).build());

        snapshotRepository.deleteById(snapshot.getId());
        assertFalse(snapshotRepository.existsById(snapshot.getId()));
    }
}