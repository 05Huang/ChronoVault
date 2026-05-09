package com.chronovault.ssh;

import com.chronovault.entity.Server;
import com.chronovault.security.CredentialEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SshConnectionManager {

    private final CredentialEncryptor encryptor;

    @Value("${chronovault.ssh.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${chronovault.ssh.command-timeout:60000}")
    private int commandTimeout;

    private SshClient client;

    private final ConcurrentHashMap<String, SshConnection> connectionPool = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.start();
    }

    @PreDestroy
    public void destroy() {
        connectionPool.values().forEach(SshConnection::close);
        connectionPool.clear();
        if (client != null) {
            try { client.stop(); } catch (Exception ignored) {}
        }
    }

    public SshConnection getConnection(Server server) throws IOException {
        String poolKey = server.getIp() + ":" + server.getSshPort();

        SshConnection existing = connectionPool.get(poolKey);
        if (existing != null && existing.isOpen()) {
            return existing;
        }

        SshConnection conn = createConnection(server);
        connectionPool.put(poolKey, conn);
        return conn;
    }

    public SshConnection getConnection(String host, int port, String username, String authMethod,
                                        String keyEncrypted, String password) throws IOException {
        String poolKey = host + ":" + port;

        SshConnection existing = connectionPool.get(poolKey);
        if (existing != null && existing.isOpen()) {
            return existing;
        }

        Server temp = new Server();
        temp.setIp(host);
        temp.setSshPort(port);
        temp.setSshUsername(username);
        temp.setSshAuthMethod(authMethod);
        temp.setSshKeyEncrypted(keyEncrypted);

        SshConnection conn = createConnection(temp);
        connectionPool.put(poolKey, conn);
        return conn;
    }

    private SshConnection createConnection(Server server) throws IOException {
        try {
            ClientSession session = client.connect(server.getSshUsername(), server.getIp(),
                    server.getSshPort() != null ? server.getSshPort() : 22)
                    .verify(connectionTimeout, TimeUnit.MILLISECONDS)
                    .getSession();

            if ("KEY".equals(server.getSshAuthMethod()) && server.getSshKeyEncrypted() != null) {
                String keyContent = encryptor.decrypt(server.getSshKeyEncrypted());
                Path tempKey = Files.createTempFile("cv_ssh_key_", "");
                Files.write(tempKey, keyContent.getBytes(StandardCharsets.UTF_8));
                try {
                    session.addPublicKeyIdentity(loadKeyPair(tempKey));
                } finally {
                    Files.deleteIfExists(tempKey);
                }
            } else if ("PASSWORD".equals(server.getSshAuthMethod()) && server.getSshKeyEncrypted() != null) {
                String password = encryptor.decrypt(server.getSshKeyEncrypted());
                session.addPasswordIdentity(password);
            }

            session.auth().verify(connectionTimeout, TimeUnit.MILLISECONDS);

            log.info("SSH connected to {}:{}", server.getIp(), server.getSshPort());
            return new SshConnection(session, client, server.getIp(),
                    server.getSshPort() != null ? server.getSshPort() : 22);
        } catch (Exception e) {
            throw new IOException("SSH connection failed to " + server.getIp() + ": " + e.getMessage(), e);
        }
    }

    private KeyPair loadKeyPair(Path keyPath) throws Exception {
        var parser = org.apache.sshd.common.config.keys.loader.openssh.OpenSSHKeyPairResourceParser.INSTANCE;
        Iterable<KeyPair> keys = parser.loadKeyPairs(null, keyPath, null);
        return keys.iterator().next();
    }

    public void removeConnection(String host, int port) {
        String poolKey = host + ":" + port;
        SshConnection conn = connectionPool.remove(poolKey);
        if (conn != null) {
            conn.close();
        }
    }
}
