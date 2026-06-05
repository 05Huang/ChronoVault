package com.chronovault.integration;

import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.User;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify transaction consistency:
 * Concurrent snapshot creation should not produce dirty data.
 */
class TransactionConsistencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ServerRepository serverRepository;
    @Autowired private SnapshotRepository snapshotRepository;

    private User testUser;
    private Server testServer;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("Concurrency Test User")
                .email("concurrency-test-" + System.nanoTime() + "@test.com")
                .passwordHash("hashed")
                .role(User.Role.MEMBER)
                .status(User.UserStatus.ONLINE)
                .build());

        testServer = serverRepository.save(Server.builder()
                .user(testUser)
                .name("concurrency-server")
                .ip("192.168.1.100")
                .os("Ubuntu 22.04")
                .status(Server.ServerStatus.RUNNING)
                .sshPort(22)
                .sshUsername("root")
                .sshAuthMethod("KEY")
                .build());
    }

    @Test
    void concurrentSnapshotCreation_noDirtyData() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Snapshot snapshot = snapshotRepository.save(Snapshot.builder()
                            .server(testServer)
                            .title("Concurrent Snapshot " + index)
                            .type(Snapshot.SnapshotType.FULL)
                            .status(Snapshot.SnapshotStatus.STABLE)
                            .hash("hash-" + index)
                            .sizeBytes(index * 1000L)
                            .build());
                    assertNotNull(snapshot.getId(), "Snapshot ID should not be null");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(threadCount, successCount.get(), "All snapshot creations should succeed");
        assertEquals(0, failureCount.get(), "No failures should occur");
        assertTrue(errors.isEmpty(), "No exceptions should be thrown");

        List<Snapshot> allSnapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(testServer.getId());
        assertEquals(threadCount, allSnapshots.size(), "All snapshots should be in database");

        Set<Long> ids = new HashSet<>();
        for (Snapshot s : allSnapshots) {
            assertNotNull(s.getId(), "Snapshot ID should not be null");
            assertTrue(ids.add(s.getId()), "Duplicate snapshot ID found: " + s.getId());
        }

        for (Snapshot s : allSnapshots) {
            assertEquals(testServer.getId(), s.getServer().getId(),
                    "Snapshot should belong to the correct server");
        }
    }

    @Test
    void concurrentMultiServerSnapshot_noCrossContamination() throws Exception {
        Server server2 = serverRepository.save(Server.builder()
                .user(testUser).name("server-2").ip("192.168.1.101")
                .os("CentOS 8").status(Server.ServerStatus.RUNNING)
                .sshPort(22).sshUsername("root").sshAuthMethod("KEY").build());

        Server server3 = serverRepository.save(Server.builder()
                .user(testUser).name("server-3").ip("192.168.1.102")
                .os("Debian 11").status(Server.ServerStatus.RUNNING)
                .sshPort(22).sshUsername("root").sshAuthMethod("KEY").build());

        int snapshotsPerServer = 5;
        ExecutorService executor = Executors.newFixedThreadPool(snapshotsPerServer * 3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(snapshotsPerServer * 3);

        for (int s = 0; s < 3; s++) {
            final Server server = (s == 0) ? testServer : (s == 1) ? server2 : server3;
            for (int i = 0; i < snapshotsPerServer; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        snapshotRepository.save(Snapshot.builder()
                                .server(server)
                                .title("Server " + server.getName() + " Snap " + index)
                                .type(Snapshot.SnapshotType.FULL)
                                .status(Snapshot.SnapshotStatus.STABLE)
                                .build());
                    } catch (Exception e) {
                        fail("Snapshot creation failed: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(snapshotsPerServer,
                snapshotRepository.findByServerIdOrderByCreatedAtDesc(testServer.getId()).size(),
                "Server 1 should have correct snapshot count");
        assertEquals(snapshotsPerServer,
                snapshotRepository.findByServerIdOrderByCreatedAtDesc(server2.getId()).size(),
                "Server 2 should have correct snapshot count");
        assertEquals(snapshotsPerServer,
                snapshotRepository.findByServerIdOrderByCreatedAtDesc(server3.getId()).size(),
                "Server 3 should have correct snapshot count");
    }

    @Test
    void concurrentReadWrite_noInconsistentReads() throws Exception {
        for (int i = 0; i < 5; i++) {
            snapshotRepository.save(Snapshot.builder()
                    .server(testServer).title("Pre-existing " + i)
                    .type(Snapshot.SnapshotType.FULL).status(Snapshot.SnapshotStatus.STABLE)
                    .hash("pre-hash-" + i).sizeBytes(i * 100L).build());
        }

        int writerCount = 3;
        int readerCount = 5;
        int snapshotsPerWriter = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(writerCount + readerCount);
        AtomicInteger readErrors = new AtomicInteger(0);
        AtomicInteger writeErrors = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(writerCount + readerCount);

        for (int w = 0; w < writerCount; w++) {
            final int writerIdx = w;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < snapshotsPerWriter; i++) {
                        snapshotRepository.save(Snapshot.builder()
                                .server(testServer)
                                .title("Writer " + writerIdx + " Snap " + i)
                                .type(Snapshot.SnapshotType.FULL)
                                .status(Snapshot.SnapshotStatus.STABLE)
                                .build());
                    }
                } catch (Exception e) {
                    writeErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (int r = 0; r < readerCount; r++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int attempt = 0; attempt < 10; attempt++) {
                        List<Snapshot> snapshots = snapshotRepository
                                .findByServerIdOrderByCreatedAtDesc(testServer.getId());
                        for (Snapshot s : snapshots) {
                            assertNotNull(s.getId(), "Snapshot ID should never be null");
                        }
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    readErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(60, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(0, writeErrors.get(), "No write errors should occur");
        assertEquals(0, readErrors.get(), "No read errors should occur");

        int expected = 5 + (writerCount * snapshotsPerWriter);
        assertEquals(expected,
                snapshotRepository.findByServerIdOrderByCreatedAtDesc(testServer.getId()).size(),
                "Final snapshot count should match expected");
    }
}
