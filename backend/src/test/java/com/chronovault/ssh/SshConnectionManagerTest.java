package com.chronovault.ssh;

import com.chronovault.entity.Server;
import com.chronovault.security.CredentialEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SshConnectionManagerTest {

    @Mock
    private CredentialEncryptor encryptor;

    @InjectMocks
    private SshConnectionManager sshManager;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sshManager, "connectionTimeout", 5000);
        ReflectionTestUtils.setField(sshManager, "commandTimeout", 10000);
        ReflectionTestUtils.setField(sshManager, "maxConnectionsPerServer", 3);
        ReflectionTestUtils.setField(sshManager, "keepaliveInterval", 30000L);
        ReflectionTestUtils.setField(sshManager, "maxRetry", 1);
        ReflectionTestUtils.setField(sshManager, "idleEvictionMillis", 300000L);
        ReflectionTestUtils.setField(sshManager, "knownHostsFile", "");
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
}
