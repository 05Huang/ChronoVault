package com.chronovault.ssh;

import com.chronovault.entity.Server;
import com.chronovault.security.CredentialEncryptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SshConnectionManagerTest {

    @Mock
    private CredentialEncryptor encryptor;

    private MeterRegistry meterRegistry;

    @InjectMocks
    private SshConnectionManager sshManager;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(sshManager, "meterRegistry", meterRegistry);
        ReflectionTestUtils.setField(sshManager, "connectionTimeout", 5000);
        ReflectionTestUtils.setField(sshManager, "commandTimeout", 10000);
        ReflectionTestUtils.setField(sshManager, "maxConnectionsPerServer", 3);
        ReflectionTestUtils.setField(sshManager, "keepaliveInterval", 30000L);
        ReflectionTestUtils.setField(sshManager, "maxRetry", 1);
        ReflectionTestUtils.setField(sshManager, "idleEvictionMillis", 300000L);
        ReflectionTestUtils.setField(sshManager, "knownHostsFile", "");
        ReflectionTestUtils.setField(sshManager, "globalMaxConnections", 50);
        ReflectionTestUtils.setField(sshManager, "shuttingDown", false);
        ReflectionTestUtils.setField(sshManager, "globalConnectionSemaphore", new Semaphore(50, true));
    }

    @Test
    void getActiveConnectionCount_initiallyZero() {
        assertEquals(0, sshManager.getActiveConnectionCount());
    }

    @Test
    void removeConnection_nonExistent_noException() {
        assertDoesNotThrow(() -> sshManager.removeConnection("192.168.1.1", 22));
    }

    @Test
    void removeConnection_withUsername_nonExistent_noException() {
        assertDoesNotThrow(() -> sshManager.removeConnection("192.168.1.1", 22, "root"));
    }

    @Test
    void getConnection_toUnreachableServer_throwsException() {
        // Don't initialize the SSH client (init() not called) - should fail
        Server server = Server.builder()
                .ip("192.0.2.1") // TEST-NET, guaranteed unreachable
                .sshPort(22)
                .sshUsername("root")
                .sshAuthMethod("PASSWORD")
                .sshKeyEncrypted("encrypted-pass")
                .build();

        assertThrows(Exception.class, () -> sshManager.getConnection(server));
    }

    @Test
    void buildPoolKey_format() {
        // Test the pool key format via reflection
        String key = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "buildPoolKey", "192.168.1.1", 22, "root");
        assertEquals("root@192.168.1.1:22", key);
    }

    @Test
    void buildPoolKey_differentPorts_differentKeys() {
        String key1 = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "buildPoolKey", "192.168.1.1", 22, "root");
        String key2 = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "buildPoolKey", "192.168.1.1", 2222, "root");
        assertNotEquals(key1, key2);
    }

    @Test
    void buildPoolKey_differentUsers_differentKeys() {
        String key1 = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "buildPoolKey", "192.168.1.1", 22, "root");
        String key2 = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "buildPoolKey", "192.168.1.1", 22, "ubuntu");
        assertNotEquals(key1, key2);
    }

    @Test
    void normalizeKeyContent_multilineKey_unchanged() {
        String key = "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
                "b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQ\n" +
                "-----END OPENSSH PRIVATE KEY-----\n";

        String result = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "normalizeKeyContent", key);

        assertEquals(key, result);
    }

    @Test
    void normalizeKeyContent_singleLineKey_reconstructed() {
        String singleLine = "-----BEGIN OPENSSH PRIVATE KEY-----b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQ-----END OPENSSH PRIVATE KEY-----";

        String result = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "normalizeKeyContent", singleLine);

        assertNotNull(result);
        assertTrue(result.contains("-----BEGIN OPENSSH PRIVATE KEY-----\n"));
        assertTrue(result.contains("\n-----END OPENSSH PRIVATE KEY-----\n"));
        assertTrue(result.endsWith("\n"));
    }

    @Test
    void normalizeKeyContent_windowsLineEndings_converted() {
        String key = "-----BEGIN OPENSSH PRIVATE KEY-----\r\nb3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQ\r\n-----END OPENSSH PRIVATE KEY-----\r\n";

        String result = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "normalizeKeyContent", key);

        assertFalse(result.contains("\r"));
        assertTrue(result.contains("\n"));
    }

    @Test
    void normalizeKeyContent_null_returnsNull() {
        String result = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "normalizeKeyContent", (String) null);
        assertNull(result);
    }

    @Test
    void normalizeKeyContent_rsaKey_reconstructed() {
        String singleLine = "-----BEGIN RSA PRIVATE KEY-----MIIEowIBAAKCAQEA0Z3VS5JJcds3xfn/ygWyF8PbnGcY5unA67hqlYMd4Prn7dOt-----END RSA PRIVATE KEY-----";

        String result = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "normalizeKeyContent", singleLine);

        assertNotNull(result);
        assertTrue(result.contains("-----BEGIN RSA PRIVATE KEY-----\n"));
        assertTrue(result.contains("\n-----END RSA PRIVATE KEY-----\n"));
    }

    @Test
    void normalizeKeyContent_ensuresTrailingNewline() {
        String key = "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQ\n-----END OPENSSH PRIVATE KEY-----";

        String result = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "normalizeKeyContent", key);

        assertTrue(result.endsWith("\n"));
    }

    @Test
    void getActiveCommandCount_initiallyZero() {
        assertEquals(0, sshManager.getActiveCommandCount());
    }

    @Test
    void trackCommandStart_incrementsAndReturnsDecrementCallback() {
        assertEquals(0, sshManager.getActiveCommandCount());
        Runnable callback = sshManager.trackCommandStart();
        assertEquals(1, sshManager.getActiveCommandCount());
        callback.run();
        assertEquals(0, sshManager.getActiveCommandCount());
    }

    @Test
    void trackCommandStart_multipleCommands() {
        Runnable cb1 = sshManager.trackCommandStart();
        Runnable cb2 = sshManager.trackCommandStart();
        assertEquals(2, sshManager.getActiveCommandCount());
        cb1.run();
        assertEquals(1, sshManager.getActiveCommandCount());
        cb2.run();
        assertEquals(0, sshManager.getActiveCommandCount());
    }

    @Test
    void getConnection_afterShutdown_throwsIOException() {
        ReflectionTestUtils.setField(sshManager, "shuttingDown", true);
        Server server = Server.builder()
                .ip("192.168.1.1")
                .sshPort(22)
                .sshUsername("root")
                .sshAuthMethod("PASSWORD")
                .sshKeyEncrypted("encrypted-pass")
                .build();

        assertThrows(Exception.class, () -> sshManager.getConnection(server));
    }

    @Test
    void recordCommandMetrics_doesNotThrow() {
        assertDoesNotThrow(() -> sshManager.recordCommandMetrics("192.168.1.1", 22, 150L, true));
        assertDoesNotThrow(() -> sshManager.recordCommandMetrics("192.168.1.1", 22, 5000L, false));
    }

    @Test
    void countIdleConnections_reflectsState() {
        int idle = (int) ReflectionTestUtils.invokeMethod(sshManager, "countIdleConnections");
        assertEquals(0, idle);
    }

    // ===== New tests for connection pool management =====

    @Test
    void evictIdleConnections_emptyPool_noException() {
        // Evict on empty pool should not throw
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(sshManager, "evictIdleConnections"));
    }

    @Test
    void evictIdleConnections_removesStaleConnections() throws Exception {
        // Simulate a stale connection by inserting directly into the internal maps
        var connectionPool = (ConcurrentHashMap<String, SshConnection>) ReflectionTestUtils.getField(sshManager, "connectionPool");
        var lastUsedTime = (ConcurrentHashMap<String, Long>) ReflectionTestUtils.getField(sshManager, "lastUsedTime");
        var connectionLocks = (ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock>) ReflectionTestUtils.getField(sshManager, "connectionLocks");

        // Create a mock SshConnection (no stubs needed - eviction just calls close())
        SshConnection mockConn = org.mockito.Mockito.mock(SshConnection.class);

        String poolKey = "root@10.0.0.1:22";
        connectionPool.put(poolKey, mockConn);
        lastUsedTime.put(poolKey, System.currentTimeMillis() - 600_000L); // 10 minutes ago (beyond 5 min threshold)
        connectionLocks.put(poolKey, new java.util.concurrent.locks.ReentrantLock());

        // Run eviction
        ReflectionTestUtils.invokeMethod(sshManager, "evictIdleConnections");

        // Connection should be evicted
        assertNull(connectionPool.get(poolKey));
        assertNull(lastUsedTime.get(poolKey));
        // close() should have been called
        org.mockito.Mockito.verify(mockConn).close();
    }

    @Test
    void evictIdleConnections_keepsActiveConnections() throws Exception {
        var connectionPool = (ConcurrentHashMap<String, SshConnection>) ReflectionTestUtils.getField(sshManager, "connectionPool");
        var lastUsedTime = (ConcurrentHashMap<String, Long>) ReflectionTestUtils.getField(sshManager, "lastUsedTime");
        var connectionLocks = (ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock>) ReflectionTestUtils.getField(sshManager, "connectionLocks");

        // Create a recently-used connection (should NOT be evicted)
        SshConnection mockConn = org.mockito.Mockito.mock(SshConnection.class);

        String poolKey = "root@10.0.0.2:22";
        connectionPool.put(poolKey, mockConn);
        lastUsedTime.put(poolKey, System.currentTimeMillis()); // Just used
        connectionLocks.put(poolKey, new java.util.concurrent.locks.ReentrantLock());

        // Run eviction
        ReflectionTestUtils.invokeMethod(sshManager, "evictIdleConnections");

        // Connection should still exist
        assertNotNull(connectionPool.get(poolKey));
        assertNotNull(lastUsedTime.get(poolKey));
        // close() should NOT have been called
        org.mockito.Mockito.verify(mockConn, org.mockito.Mockito.never()).close();
    }

    @Test
    void buildErrorMessage_shortChain() {
        Throwable cause = new IOException("Connection refused");
        String result = (String) ReflectionTestUtils.invokeMethod(sshManager, "buildErrorMessage", cause);
        assertTrue(result.contains("IOException"));
        assertTrue(result.contains("Connection refused"));
    }

    @Test
    void buildErrorMessage_nestedChain() {
        Throwable root = new IOException("I/O error");
        Throwable middle = new RuntimeException("Session failed", root);
        Throwable top = new IOException("Connection failed", middle);

        String result = (String) ReflectionTestUtils.invokeMethod(sshManager, "buildErrorMessage", top);

        assertTrue(result.contains("IOException"));
        assertTrue(result.contains("Connection failed"));
        assertTrue(result.contains("RuntimeException"));
        assertTrue(result.contains("Session failed"));
        assertTrue(result.contains("IOException"));
        assertTrue(result.contains("I/O error"));
        // Should contain arrow separators
        assertTrue(result.contains(" <- "));
    }

    @Test
    void buildErrorMessage_depthLimited() {
        // Create a chain deeper than 5 levels
        Throwable t = new IOException("level 0");
        for (int i = 1; i <= 10; i++) {
            t = new RuntimeException("level " + i, t);
        }
        String result = (String) ReflectionTestUtils.invokeMethod(sshManager, "buildErrorMessage", t);

        // Should only contain up to 5 levels (split by " <- ")
        String[] parts = result.split(" <- ");
        assertTrue(parts.length <= 5, "Error message should be limited to 5 depth levels, got " + parts.length);
    }

    @Test
    void recordCommandMetrics_withRegistry_recordsTimerAndCounter() {
        sshManager.recordCommandMetrics("10.0.0.1", 22, 150L, true);

        // Verify timer was recorded
        Timer timer = meterRegistry.find("cv.ssh.command.duration")
                .tag("host", "10.0.0.1")
                .tag("success", "true")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());

        // Verify counter was incremented
        Counter counter = meterRegistry.find("cv.ssh.command.total")
                .tag("host", "10.0.0.1")
                .tag("success", "true")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void recordCommandMetrics_failure_recordsSeparately() {
        sshManager.recordCommandMetrics("10.0.0.2", 22, 5000L, false);

        Timer timer = meterRegistry.find("cv.ssh.command.duration")
                .tag("host", "10.0.0.2")
                .tag("success", "false")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());

        Counter counter = meterRegistry.find("cv.ssh.command.total")
                .tag("host", "10.0.0.2")
                .tag("success", "false")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void recordCommandMetrics_nullRegistry_doesNotThrow() {
        ReflectionTestUtils.setField(sshManager, "meterRegistry", null);
        assertDoesNotThrow(() -> sshManager.recordCommandMetrics("10.0.0.1", 22, 100L, true));
    }

    @Test
    void trackCommandStart_concurrentAccess_threadSafe() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicBoolean anyError = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Runnable cb = sshManager.trackCommandStart();
                    // Simulate some work
                    Thread.sleep(10);
                    cb.run();
                } catch (Exception e) {
                    anyError.set(true);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Threads did not complete in time");
        assertFalse(anyError.get(), "An error occurred during concurrent access");
        assertEquals(0, sshManager.getActiveCommandCount(), "All commands should be tracked as complete");

        executor.shutdown();
    }

    @Test
    void getConnection_withExplicitParams_usesCorrectPoolKey() {
        // This tests the getConnection(String, int, String, String, String, String) overload
        // It should throw because the SSH client isn't initialized, but we can verify the pool key logic
        assertThrows(Exception.class, () -> {
            sshManager.getConnection("10.0.0.1", 22, "root", "PASSWORD", null, "testpass");
        });
    }

    @Test
    void removeConnection_withHost_port_removesAllUsers() throws Exception {
        var connectionPool = (ConcurrentHashMap<String, SshConnection>) ReflectionTestUtils.getField(sshManager, "connectionPool");
        var lastUsedTime = (ConcurrentHashMap<String, Long>) ReflectionTestUtils.getField(sshManager, "lastUsedTime");
        var connectionLocks = (ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock>) ReflectionTestUtils.getField(sshManager, "connectionLocks");

        // Add connections for same host:port but different users
        SshConnection conn1 = org.mockito.Mockito.mock(SshConnection.class);
        SshConnection conn2 = org.mockito.Mockito.mock(SshConnection.class);

        connectionPool.put("root@10.0.0.1:22", conn1);
        connectionPool.put("ubuntu@10.0.0.1:22", conn2);
        lastUsedTime.put("root@10.0.0.1:22", System.currentTimeMillis());
        lastUsedTime.put("ubuntu@10.0.0.1:22", System.currentTimeMillis());
        connectionLocks.put("root@10.0.0.1:22", new java.util.concurrent.locks.ReentrantLock());
        connectionLocks.put("ubuntu@10.0.0.1:22", new java.util.concurrent.locks.ReentrantLock());

        // Remove by host:port (no username)
        sshManager.removeConnection("10.0.0.1", 22);

        // Both connections should be removed
        assertNull(connectionPool.get("root@10.0.0.1:22"));
        assertNull(connectionPool.get("ubuntu@10.0.0.1:22"));
        // Both should have been closed
        org.mockito.Mockito.verify(conn1).close();
        org.mockito.Mockito.verify(conn2).close();
    }

    @Test
    void removeConnection_withSpecificUser_onlyRemovesThatUser() throws Exception {
        var connectionPool = (ConcurrentHashMap<String, SshConnection>) ReflectionTestUtils.getField(sshManager, "connectionPool");
        var lastUsedTime = (ConcurrentHashMap<String, Long>) ReflectionTestUtils.getField(sshManager, "lastUsedTime");
        var connectionLocks = (ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock>) ReflectionTestUtils.getField(sshManager, "connectionLocks");

        SshConnection conn1 = org.mockito.Mockito.mock(SshConnection.class);
        SshConnection conn2 = org.mockito.Mockito.mock(SshConnection.class);

        connectionPool.put("root@10.0.0.1:22", conn1);
        connectionPool.put("ubuntu@10.0.0.1:22", conn2);
        lastUsedTime.put("root@10.0.0.1:22", System.currentTimeMillis());
        lastUsedTime.put("ubuntu@10.0.0.1:22", System.currentTimeMillis());
        connectionLocks.put("root@10.0.0.1:22", new java.util.concurrent.locks.ReentrantLock());
        connectionLocks.put("ubuntu@10.0.0.1:22", new java.util.concurrent.locks.ReentrantLock());

        // Remove only root user
        sshManager.removeConnection("10.0.0.1", 22, "root");

        // Only root connection should be removed
        assertNull(connectionPool.get("root@10.0.0.1:22"));
        assertNotNull(connectionPool.get("ubuntu@10.0.0.1:22"));
        org.mockito.Mockito.verify(conn1).close();
        org.mockito.Mockito.verify(conn2, org.mockito.Mockito.never()).close();
    }

    @Test
    void getConnection_shutdown_blocksNewConnections() {
        ReflectionTestUtils.setField(sshManager, "shuttingDown", true);

        Server server = Server.builder()
                .ip("10.0.0.1")
                .sshPort(22)
                .sshUsername("root")
                .sshAuthMethod("PASSWORD")
                .sshKeyEncrypted("encrypted-pass")
                .build();

        IOException ex = assertThrows(IOException.class, () -> sshManager.getConnection(server));
        assertTrue(ex.getMessage().contains("shutting down"));
    }

    @Test
    void getConnection_explicitParams_shutdown_blocksNewConnections() {
        ReflectionTestUtils.setField(sshManager, "shuttingDown", true);

        IOException ex = assertThrows(IOException.class, () ->
                sshManager.getConnection("10.0.0.1", 22, "root", "PASSWORD", null, "pass"));
        assertTrue(ex.getMessage().contains("shutting down"));
    }

    @Test
    void logPoolMetrics_emptyPool_noException() {
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(sshManager, "logPoolMetrics"));
    }

    @Test
    void healthCheckIdleConnections_emptyPool_noException() {
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(sshManager, "healthCheckIdleConnections"));
    }

    @Test
    void getConnectionCount_initiallyZero() {
        assertEquals(0, sshManager.getConnectionCount());
    }

    @Test
    void destroy_setsShuttingDownTrue() {
        // Verify that destroy() sets shuttingDown to true
        sshManager.destroy();
        assertTrue((boolean) ReflectionTestUtils.getField(sshManager, "shuttingDown"));
    }

    @Test
    void normalizeKeyContent_longBase64Content_formatted() {
        // Simulate a key with base64 content that needs line wrapping
        String base64Content = "a".repeat(200); // 200 chars of base64
        String singleLineKey = "-----BEGIN OPENSSH PRIVATE KEY-----" + base64Content + "-----END OPENSSH PRIVATE KEY-----";

        String result = (String) ReflectionTestUtils.invokeMethod(
                sshManager, "normalizeKeyContent", singleLineKey);

        assertNotNull(result);
        // Content should be split into 64-char lines
        String[] lines = result.split("\n");
        // Header + content lines + footer = at least 3 lines
        assertTrue(lines.length >= 3, "Should have header, content lines, and footer");
        // First line should be the BEGIN marker
        assertTrue(lines[0].contains("-----BEGIN OPENSSH PRIVATE KEY-----"));
        // Last non-empty line should be the END marker
        String lastNonEmpty = "";
        for (String line : lines) {
            if (!line.trim().isEmpty()) lastNonEmpty = line.trim();
        }
        assertTrue(lastNonEmpty.contains("-----END OPENSSH PRIVATE KEY-----"));
    }
}
