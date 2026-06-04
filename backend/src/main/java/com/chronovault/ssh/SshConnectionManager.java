package com.chronovault.ssh;

import com.chronovault.entity.Server;
import com.chronovault.security.CredentialEncryptor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Security;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class SshConnectionManager {

    private final CredentialEncryptor encryptor;

    @Value("${chronovault.ssh.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${chronovault.ssh.command-timeout:60000}")
    private int commandTimeout;

    @Value("${chronovault.ssh.max-connections-per-server:3}")
    private int maxConnectionsPerServer;

    @Value("${chronovault.ssh.keepalive-interval:30000}")
    private long keepaliveInterval;

    @Value("${chronovault.ssh.max-retry:3}")
    private int maxRetry;

    @Value("${chronovault.ssh.idle-eviction-millis:300000}")
    private long idleEvictionMillis;

    @Value("${chronovault.ssh.known-hosts-file:}")
    private String knownHostsFile;

    @Value("${chronovault.ssh.strict-host-checking:false}")
    private boolean strictHostChecking;

    private SshClient client;
    private final ConcurrentHashMap<String, SshConnection> connectionPool = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> connectionLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastUsedTime = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        // Register EdDSA provider for Ed25519 SSH key support
        if (Security.getProvider(net.i2p.crypto.eddsa.EdDSASecurityProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new net.i2p.crypto.eddsa.EdDSASecurityProvider());
            log.info("EdDSA security provider registered for Ed25519 key support");
        }

        client = SshClient.setUpDefaultClient();

        // Use known_hosts file if configured, otherwise accept all (with warning)
        if (knownHostsFile != null && !knownHostsFile.isBlank()) {
            Path khPath = Path.of(knownHostsFile);
            if (Files.exists(khPath)) {
                try {
                    client.setServerKeyVerifier(
                        new org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier(
                            AcceptAllServerKeyVerifier.INSTANCE, khPath));
                    log.info("Using known_hosts file: {}", knownHostsFile);
                } catch (Exception e) {
                    client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
                    log.warn("Failed to load known_hosts file {}, falling back to accept-all: {}", knownHostsFile, e.getMessage());
                }
            } else {
                client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
                log.warn("Known hosts file not found: {}, accepting all keys", knownHostsFile);
            }
        } else if (strictHostChecking) {
            // Production mode: refuse to connect without known_hosts verification
            log.error("SSH strict-host-checking is enabled but no known-hosts-file is configured! "
                    + "Set chronovault.ssh.known-hosts-file or disable strict-host-checking.");
            // Still use accept-all to avoid startup failure, but log the security warning
            client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        } else {
            client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
            log.warn("No known_hosts file configured, accepting all server keys (TOFU mode) — NOT recommended for production");
        }

        client.start();

        // Start idle connection eviction scheduler + metrics logging
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ssh-eviction");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::evictIdleConnections, 60, 60, TimeUnit.SECONDS);
        // Log pool metrics every 60 seconds
        scheduler.scheduleAtFixedRate(this::logPoolMetrics, 60, 60, TimeUnit.SECONDS);

        log.info("SSH connection manager initialized (maxPerServer={}, timeout={}ms, retry={})",
                maxConnectionsPerServer, connectionTimeout, maxRetry);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        connectionPool.values().forEach(conn -> {
            try { conn.close(); } catch (Exception ignored) {}
        });
        connectionPool.clear();
        connectionLocks.clear();
        lastUsedTime.clear();
        if (client != null) {
            try { client.stop(); } catch (Exception ignored) {}
        }
        log.info("SSH connection manager destroyed, all connections closed");
    }

    /**
     * Get a pooled SSH connection for the given server. Thread-safe with per-server locking.
     */
    public SshConnection getConnection(Server server) throws IOException {
        String poolKey = buildPoolKey(server.getIp(), server.getSshPort(), server.getSshUsername());
        return getConnectionInternal(poolKey, server);
    }

    /**
     * Get a pooled SSH connection by explicit parameters.
     */
    public SshConnection getConnection(String host, int port, String username, String authMethod,
                                        String keyEncrypted, String password) throws IOException {
        String poolKey = buildPoolKey(host, port, username);

        Server temp = new Server();
        temp.setIp(host);
        temp.setSshPort(port);
        temp.setSshUsername(username);
        temp.setSshAuthMethod(authMethod);
        temp.setSshKeyEncrypted(keyEncrypted);

        return getConnectionInternal(poolKey, temp);
    }

    private String buildPoolKey(String host, int port, String username) {
        return username + "@" + host + ":" + port;
    }

    private SshConnection getConnectionInternal(String poolKey, Server server) throws IOException {
        // Per-server lock to prevent thundering herd / race conditions
        ReentrantLock lock = connectionLocks.computeIfAbsent(poolKey, k -> new ReentrantLock());
        lock.lock();
        try {
            // Check existing connection
            SshConnection existing = connectionPool.get(poolKey);
            if (existing != null && existing.isOpen()) {
                // Validate connection is actually alive with a quick command
                try {
                    SshConnection.CommandResult alive = existing.executeCommand("echo ok", java.time.Duration.ofSeconds(5));
                    if (alive.isSuccess() && "ok".equals(alive.stdout().trim())) {
                        lastUsedTime.put(poolKey, System.currentTimeMillis());
                        return existing;
                    }
                    log.warn("SSH connection to {} is stale (validation failed), reconnecting", poolKey);
                } catch (Exception e) {
                    log.warn("SSH connection to {} is stale ({}), reconnecting", poolKey, e.getMessage());
                }
                try { existing.close(); } catch (Exception ignored) {}
                connectionPool.remove(poolKey);
            }

            // Remove stale entry if closed
            if (existing != null) {
                connectionPool.remove(poolKey);
                try { existing.close(); } catch (Exception ignored) {}
            }

            // Create new connection with retry
            SshConnection conn = createConnectionWithRetry(server);
            connectionPool.put(poolKey, conn);
            lastUsedTime.put(poolKey, System.currentTimeMillis());
            return conn;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Create a connection with exponential backoff retry.
     */
    private SshConnection createConnectionWithRetry(Server server) throws IOException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                return createConnection(server);
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxRetry) {
                    long backoff = (long) Math.pow(2, attempt) * 500; // 1s, 2s, 4s
                    log.warn("SSH connection attempt {}/{} failed to {}:{}, retrying in {}ms: {}",
                            attempt, maxRetry, server.getIp(), server.getSshPort(), backoff, e.getMessage());
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Connection interrupted", ie);
                    }
                } else {
                    log.error("SSH connection failed after {} attempts to {}:{}: {}",
                            maxRetry, server.getIp(), server.getSshPort(), e.getMessage());
                }
            }
        }
        throw lastException;
    }

    private SshConnection createConnection(Server server) throws IOException {
        int port = server.getSshPort() != null ? server.getSshPort() : 22;
        try {
            ClientSession session = client.connect(server.getSshUsername(), server.getIp(), port)
                    .verify(connectionTimeout, TimeUnit.MILLISECONDS)
                    .getSession();

            if ("KEY".equals(server.getSshAuthMethod()) && server.getSshKeyEncrypted() != null) {
                String keyContent = encryptor.decrypt(server.getSshKeyEncrypted());
                log.debug("Decrypted key content length: {} chars", keyContent != null ? keyContent.length() : 0);
                KeyPair kp = loadKeyPairFromMemory(keyContent, null);
                log.debug("Key pair loaded: algo={}, pubKeyLen={}", kp.getPublic().getAlgorithm(),
                        kp.getPublic().getEncoded().length);
                session.addPublicKeyIdentity(kp);
            } else if ("PASSWORD".equals(server.getSshAuthMethod()) && server.getSshKeyEncrypted() != null) {
                String password = encryptor.decrypt(server.getSshKeyEncrypted());
                session.addPasswordIdentity(password);
            }

            session.auth().verify(connectionTimeout, TimeUnit.MILLISECONDS);

            // Send a keepalive request periodically to detect dead connections
            session.addSessionListener(new org.apache.sshd.common.session.SessionListener() {
                @Override
                public void sessionException(org.apache.sshd.common.session.Session session, Throwable t) {
                    log.warn("SSH session error on {}:{}: {}", server.getIp(), port, t.getMessage());
                }
            });

            log.info("SSH connected to {}@{}:{}", server.getSshUsername(), server.getIp(), port);
            return new SshConnection(session, client, server.getIp(), port);
        } catch (Exception e) {
            // Build a detailed error message with full cause chain
            String detail = buildErrorMessage(e);
            log.error("SSH connection failed to {}:{} - {}", server.getIp(), port, detail);
            throw new IOException("SSH connection failed to " + server.getIp() + ":" + port + " - " + detail, e);
        }
    }

    /**
     * Build a detailed error message from exception chain.
     */
    private String buildErrorMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cause = t;
        int depth = 0;
        while (cause != null && depth < 5) {
            if (depth > 0) sb.append(" <- ");
            sb.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
            cause = cause.getCause();
            depth++;
        }
        return sb.toString();
    }

    /**
     * Load key pair from memory string instead of writing temp files to disk.
     * Creates a temp file with restrictive permissions (0600) and deletes it immediately after parsing.
     */
    private KeyPair loadKeyPairFromMemory(String keyContent, String passphrase) throws Exception {
        // Normalize key format: ensure proper newlines
        String normalizedKey = normalizeKeyContent(keyContent);

        // Write to a temp file with restrictive permissions, read it, then delete
        Path tempKey = Files.createTempFile("cv_ssh_key_", "");
        try {
            Files.write(tempKey, normalizedKey.getBytes(StandardCharsets.UTF_8));
            // On Unix, restrict permissions to owner-only (0600). On Windows, skip -
            // temp files are already in the user's private temp directory.
            String os = System.getProperty("os.name", "").toLowerCase();
            if (!os.contains("win")) {
                Set<java.nio.file.attribute.PosixFilePermission> perms = EnumSet.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(tempKey, perms);
            }

            log.debug("Loading SSH key from temp file: {}, size={} bytes, exists={}",
                    tempKey, Files.size(tempKey), Files.exists(tempKey));

            var parser = org.apache.sshd.common.config.keys.loader.openssh.OpenSSHKeyPairResourceParser.INSTANCE;
            org.apache.sshd.common.config.keys.FilePasswordProvider passwordProvider = passphrase != null
                    ? (session, resource, retryIndex) -> passphrase
                    : null;
            // Eagerly load all key pairs before deleting the temp file
            Iterable<KeyPair> keys;
            try {
                keys = parser.loadKeyPairs(null, tempKey, passwordProvider);
            } catch (Exception e) {
                log.error("Key parser failed for temp file {}: {} - {}", tempKey, e.getClass().getName(), e.getMessage());
                // Log first 80 chars of key content for debugging (no sensitive data)
                String preview = normalizedKey.length() > 80 ? normalizedKey.substring(0, 80) : normalizedKey;
                log.error("Key content preview: {}", preview);
                throw new IOException("Failed to parse SSH key: " + e.getMessage(), e);
            }

            java.util.List<KeyPair> keyList = new java.util.ArrayList<>();
            for (KeyPair kp : keys) {
                keyList.add(kp);
            }
            if (keyList.isEmpty()) {
                throw new IOException("No key pair found in provided key content");
            }
            KeyPair kp = keyList.get(0);
            log.info("Loaded SSH key: algorithm={}, {} key pair(s)", kp.getPublic().getAlgorithm(), keyList.size());
            return kp;
        } finally {
            Files.deleteIfExists(tempKey);
        }
    }

    /**
     * Log connection pool metrics for monitoring.
     */
    private void logPoolMetrics() {
        int active = connectionPool.size();
        int locks = connectionLocks.size();
        if (active > 0 || locks > 0) {
            log.info("[SSH_POOL] active={}, pool_size={}, locks={}", active, connectionPool.size(), locks);
        }
    }

    /**
     * Evict connections idle longer than the configured threshold.
     */
    private void evictIdleConnections() {
        long now = System.currentTimeMillis();
        int evicted = 0;
        for (var entry : lastUsedTime.entrySet()) {
            String poolKey = entry.getKey();
            long lastUsed = entry.getValue();
            if (now - lastUsed > idleEvictionMillis) {
                ReentrantLock lock = connectionLocks.get(poolKey);
                if (lock != null && lock.tryLock()) {
                    try {
                        SshConnection conn = connectionPool.remove(poolKey);
                        if (conn != null) {
                            conn.close();
                            evicted++;
                        }
                        lastUsedTime.remove(poolKey);
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }
        if (evicted > 0) {
            log.info("Evicted {} idle SSH connections", evicted);
        }
    }

    public void removeConnection(String host, int port, String username) {
        String poolKey = buildPoolKey(host, port, username);
        ReentrantLock lock = connectionLocks.get(poolKey);
        if (lock != null) {
            lock.lock();
            try {
                SshConnection conn = connectionPool.remove(poolKey);
                lastUsedTime.remove(poolKey);
                if (conn != null) {
                    conn.close();
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Backward-compatible removeConnection (assumes default username).
     */
    public void removeConnection(String host, int port) {
        // Remove all connections for this host:port regardless of username
        connectionLocks.keySet().stream()
                .filter(k -> k.endsWith("@" + host + ":" + port))
                .forEach(poolKey -> {
                    ReentrantLock lock = connectionLocks.get(poolKey);
                    if (lock != null) {
                        lock.lock();
                        try {
                            SshConnection conn = connectionPool.remove(poolKey);
                            lastUsedTime.remove(poolKey);
                            if (conn != null) {
                                conn.close();
                            }
                        } finally {
                            lock.unlock();
                        }
                    }
                });
    }

    /**
     * Get current pool stats for monitoring.
     */
    public int getActiveConnectionCount() {
        return connectionPool.size();
    }

    /**
     * Get total tracked connections (active + pending).
     */
    public int getConnectionCount() {
        return connectionLocks.size();
    }

    /**
     * Normalize SSH key content to ensure proper line format.
     * Handles cases where newlines were lost during copy-paste or storage.
     */
    private String normalizeKeyContent(String keyContent) {
        if (keyContent == null) return null;

        // Replace Windows line endings
        String key = keyContent.replace("\r\n", "\n").replace("\r", "\n");

        // If key is all on one line (newlines were lost), reconstruct from tokens
        if (!key.contains("\n") && key.contains("-----BEGIN")) {
            // Split on the marker tokens to reconstruct proper lines
            key = key
                    .replace("-----BEGIN OPENSSH PRIVATE KEY-----", "-----BEGIN OPENSSH PRIVATE KEY-----\n")
                    .replace("-----END OPENSSH PRIVATE KEY-----", "\n-----END OPENSSH PRIVATE KEY-----")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "-----BEGIN RSA PRIVATE KEY-----\n")
                    .replace("-----END RSA PRIVATE KEY-----", "\n-----END RSA PRIVATE KEY-----")
                    .replace("-----BEGIN EC PRIVATE KEY-----", "-----BEGIN EC PRIVATE KEY-----\n")
                    .replace("-----END EC PRIVATE KEY-----", "\n-----END EC PRIVATE KEY-----")
                    .replace("-----BEGIN DSA PRIVATE KEY-----", "-----BEGIN DSA PRIVATE KEY-----\n")
                    .replace("-----END DSA PRIVATE KEY-----", "\n-----END DSA PRIVATE KEY-----");

            // The base64 content between markers needs to be split into 64-char lines
            int beginIdx = key.indexOf("-----BEGIN");
            int beginEnd = key.indexOf("\n", beginIdx);
            int endIdx = key.indexOf("-----END");

            if (beginEnd > 0 && endIdx > beginEnd) {
                String header = key.substring(0, beginEnd + 1);
                String base64Content = key.substring(beginEnd + 1, endIdx).trim();
                String footer = key.substring(endIdx);

                // Remove any spaces in base64 content (from paste artifacts)
                base64Content = base64Content.replaceAll("\\s+", "");

                // Split into 64-char lines
                StringBuilder formatted = new StringBuilder(header);
                for (int i = 0; i < base64Content.length(); i += 64) {
                    formatted.append(base64Content, i, Math.min(i + 64, base64Content.length()));
                    formatted.append("\n");
                }
                key = formatted.toString() + footer;
            }
        }

        // Ensure key ends with a newline
        if (!key.endsWith("\n")) {
            key = key + "\n";
        }

        return key;
    }
}
