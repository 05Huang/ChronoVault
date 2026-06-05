package com.chronovault.service;

import com.chronovault.dto.server.*;
import com.chronovault.cache.CacheService;
import com.chronovault.cache.CacheKeyBuilder;
import com.chronovault.docker.DockerOperationService;
import com.chronovault.entity.*;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.*;
import com.chronovault.security.CredentialEncryptor;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository serverRepository;
    private final ContainerRepository containerRepository;
    private final VolumeRepository volumeRepository;
    private final ServerBranchRepository branchRepository;
    private final UserService userService;
    private final DockerOperationService dockerService;
    private final SshConnectionManager sshManager;
    private final CredentialEncryptor credentialEncryptor;
    private final CacheService cacheService;
    private final StateCollectionService stateCollectionService;

    public List<ServerDTO> getServers(String email) {
        String cacheKey = CacheKeyBuilder.servers(email);
        List<ServerDTO> cached = cacheService.get(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            log.debug("[SERVER_LIST] Cache hit for user={}", email);
            return cached;
        }

        User user = userService.getByEmail(email);
        List<ServerDTO> servers = serverRepository.findByUserId(user.getId()).stream()
                .map(ServerDTO::from)
                .toList();
        cacheService.put(cacheKey, servers, CacheKeyBuilder.SERVERS_TTL);
        log.debug("[SERVER_LIST] Cached {} servers for user={} (TTL=30s)", servers.size(), email);
        return servers;
    }

    public ServerDTO getServer(Long id) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + id));
        return ServerDTO.from(server);
    }

    @Transactional
    public ServerDTO createServer(String email, String name, String ip, String os) {
        User user = userService.getByEmail(email);
        Server server = Server.builder()
                .user(user)
                .name(name)
                .ip(ip)
                .os(os != null ? os : "Linux")
                .status(Server.ServerStatus.STOPPED) // Unknown until SSH probe succeeds
                .uptimeSeconds(0L)
                .build();
        serverRepository.save(server);

        // Create default "main" branch for the new server
        ServerBranch mainBranch = ServerBranch.builder()
                .server(server)
                .name("main")
                .description("默认分支")
                .isDefault(true)
                .build();
        branchRepository.save(mainBranch);

        // Probe SSH to set real status
        try {
            SshConnection conn = sshManager.getConnection(server);
            SshConnection.CommandResult result = conn.executeCommand("uname -srm && cat /proc/uptime 2>/dev/null | awk '{print int($1)}'");
            if (result.isSuccess()) {
                server.setStatus(Server.ServerStatus.RUNNING);
                String[] lines = result.stdout().trim().split("\n");
                if (lines.length >= 1) {
                    server.setOs(lines[0].length() > 100 ? lines[0].substring(0, 100) : lines[0]);
                }
                if (lines.length >= 2) {
                    try { server.setUptimeSeconds(Long.parseLong(lines[1].trim())); } catch (NumberFormatException ignored) {}
                }
                serverRepository.save(server);
            }
        } catch (Exception e) {
            log.warn("[SERVER_CONNECT] [server={}] SSH probe failed: {}", server.getId(), e.getMessage());
        }

        // Trigger async container/volume scan
        try {
            refreshContainers(server);
            refreshVolumes(server);
        } catch (Exception e) {
            log.warn("[SERVER_CONNECT] [server={}] Initial scan failed: {}", server.getId(), e.getMessage());
        }

        cacheService.evict(CacheKeyBuilder.servers(email));
        cacheService.evict(CacheKeyBuilder.dashboardOverview());
        return ServerDTO.from(server);
    }

    @Transactional
    public List<ContainerDTO> getContainers(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        // Always refresh from real Docker to get live CPU/memory stats
        try {
            refreshContainers(server);
        } catch (Exception e) {
            log.warn("[SERVER_CONTAINER] [server={}] Failed to refresh containers: {}", serverId, e.getMessage());
        }

        List<Container> containers = containerRepository.findByServerId(serverId);
        return containers.stream().map(ContainerDTO::from).toList();
    }

    @Transactional
    public List<VolumeDTO> getVolumes(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        List<Volume> volumes = volumeRepository.findByServerId(serverId);
        if (volumes.isEmpty()) {
            try {
                refreshVolumes(server);
                volumes = volumeRepository.findByServerId(serverId);
            } catch (Exception e) {
                log.warn("[SERVER_VOLUME] [server={}] Failed to refresh volumes: {}", serverId, e.getMessage());
            }
        }

        return volumes.stream().map(VolumeDTO::from).toList();
    }

    @Transactional
    public VolumeDTO addVolume(Long serverId, String containerPath, String hostPath) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));
        Volume volume = Volume.builder()
                .server(server)
                .name(containerPath)
                .containerPath(containerPath)
                .hostPath(hostPath)
                .sizeBytes(0L)
                .status("ACTIVE")
                .build();
        volumeRepository.save(volume);
        return VolumeDTO.from(volume);
    }

    public List<LogEntryDTO> getLogs(Long serverId, int tail) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        try {
            List<Container> containers = containerRepository.findByServerId(serverId);
            List<LogEntryDTO> allLogs = new ArrayList<>();

            for (Container container : containers) {
                String logs = dockerService.getContainerLogs(server, container.getName(), tail);
                for (String line : logs.lines().toList()) {
                    if (line.isBlank()) continue;
                    allLogs.add(new LogEntryDTO(
                            LocalDateTime.now().toString(),
                            classifyLogLevel(line),
                            line,
                            container.getName()
                    ));
                }
            }

            if (allLogs.isEmpty()) {
                return generateFallbackLogs();
            }
            return allLogs;

        } catch (Exception e) {
            log.warn("[SERVER_LOG] [server={}] Failed to get real logs: {}", server.getId(), e.getMessage());
            return generateFallbackLogs();
        }
    }

    @Transactional
    public void refreshContainers(Server server) {
        try {
            List<Container> containers = dockerService.listContainers(server);
            containerRepository.deleteByServerId(server.getId());
            containerRepository.saveAll(containers);
        } catch (Exception e) {
            log.warn("[SERVER_CONTAINER] [server={}] Failed to refresh containers: {}", server.getId(), e.getMessage());
        }
    }

    @Transactional
    public void refreshVolumes(Server server) {
        try {
            List<Volume> volumes = dockerService.listVolumes(server);
            volumeRepository.deleteByServerId(server.getId());
            volumeRepository.saveAll(volumes);
        } catch (Exception e) {
            log.warn("[SERVER_VOLUME] [server={}] Failed to refresh volumes: {}", server.getId(), e.getMessage());
        }
    }

    @Transactional
    public ServerDTO updateSshConfig(Long serverId, Integer port, String username, String authMethod, String credential) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));
        if (port != null) server.setSshPort(port);
        if (username != null) server.setSshUsername(username);
        if (authMethod != null) server.setSshAuthMethod(authMethod);
        if (credential != null && !credential.isBlank()) {
            // Normalize SSH key format if using key auth
            String processedCredential = credential;
            if ("KEY".equals(authMethod)) {
                processedCredential = normalizeSshKey(credential);
                if (processedCredential == null) {
                    throw new IllegalArgumentException("无效的 SSH 密钥格式。密钥必须包含 -----BEGIN 和 -----END 标记");
                }
            }
            server.setSshKeyEncrypted(credentialEncryptor.encrypt(processedCredential));
        }
        serverRepository.save(server);
        return ServerDTO.from(server);
    }

    private String normalizeSshKey(String key) {
        if (key == null) return null;
        String k = key.replace("\r\n", "\n").replace("\r", "\n");

        if (!k.contains("-----BEGIN")) return null;
        if (!k.contains("-----END")) return null;

        // If key is on a single line (newlines lost), reconstruct
        if (!k.contains("\n-----END")) {
            k = k.replace("-----BEGIN OPENSSH PRIVATE KEY-----", "-----BEGIN OPENSSH PRIVATE KEY-----\n")
                 .replace("-----END OPENSSH PRIVATE KEY-----", "\n-----END OPENSSH PRIVATE KEY-----")
                 .replace("-----BEGIN RSA PRIVATE KEY-----", "-----BEGIN RSA PRIVATE KEY-----\n")
                 .replace("-----END RSA PRIVATE KEY-----", "\n-----END RSA PRIVATE KEY-----")
                 .replace("-----BEGIN EC PRIVATE KEY-----", "-----BEGIN EC PRIVATE KEY-----\n")
                 .replace("-----END EC PRIVATE KEY-----", "\n-----END EC PRIVATE KEY-----");

            int beginEnd = k.indexOf("\n", k.indexOf("-----BEGIN"));
            int endIdx = k.indexOf("-----END");
            if (beginEnd > 0 && endIdx > beginEnd) {
                String header = k.substring(0, beginEnd + 1);
                String b64 = k.substring(beginEnd + 1, endIdx).replaceAll("\\s+", "");
                String footer = k.substring(endIdx);
                StringBuilder sb = new StringBuilder(header);
                for (int i = 0; i < b64.length(); i += 64) {
                    sb.append(b64, i, Math.min(i + 64, b64.length())).append("\n");
                }
                k = sb.toString() + footer;
            }
        }
        if (!k.endsWith("\n")) k += "\n";
        return k;
    }

    public Map<String, Object> testConnection(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        if (server.getSshKeyEncrypted() == null || server.getSshKeyEncrypted().isBlank()) {
            return Map.of("success", false, "message", "未配置 SSH 凭据，请先设置认证信息");
        }

        try {
            SshConnection conn = sshManager.getConnection(server);
            SshConnection.CommandResult result = conn.executeCommand("uname -a");
            if (result.isSuccess()) {
                String osInfo = result.stdout().trim();
                if (server.getOs() == null || server.getOs().isBlank() || server.getOs().startsWith("Ubuntu")) {
                    server.setOs(osInfo.length() > 100 ? osInfo.substring(0, 100) : osInfo);
                    serverRepository.save(server);
                }
                return Map.of("success", true, "message", "连接成功", "osInfo", osInfo);
            } else {
                return Map.of("success", false, "message", "命令执行失败: " + result.stderr());
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            String cause = e.getCause() != null ? e.getCause().getMessage() : "";

            if (errorMsg.contains("timeout") || cause.contains("timeout") || cause.contains("Timed out")) {
                return Map.of("success", false, "message",
                        String.format("连接超时 (%s:%d)。请检查：\n1. 服务器 IP 和端口是否正确\n2. 服务器防火墙是否开放 SSH 端口\n3. 网络是否可达",
                                server.getIp(), server.getSshPort() != null ? server.getSshPort() : 22));
            }
            if (errorMsg.contains("Auth fail") || cause.contains("Auth fail") || errorMsg.contains("auth")) {
                return Map.of("success", false, "message", "认证失败。请检查用户名和密钥/密码是否正确");
            }
            if (errorMsg.contains("Connection refused") || cause.contains("Connection refused")) {
                return Map.of("success", false, "message",
                        String.format("连接被拒绝 (%s:%d)。服务器 SSH 服务可能未启动或端口不正确", server.getIp(), server.getSshPort()));
            }
            if (errorMsg.contains("No route to host") || cause.contains("No route to host")) {
                return Map.of("success", false, "message", "无法到达服务器。请检查网络连接和防火墙设置");
            }
            return Map.of("success", false, "message", "连接失败: " + errorMsg);
        }
    }

    public void clearLogs(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));
        try {
            SshConnection conn = sshManager.getConnection(server);
            // Truncate all container log files
            conn.executeCommand("find /var/lib/docker/containers/ -name '*-json.log' -exec truncate -s 0 {} \\; 2>/dev/null || true");
            log.info("[SERVER_LOG] [server={}] Cleared Docker container logs", serverId);
        } catch (Exception e) {
            log.warn("[SERVER_LOG] [server={}] Failed to clear logs: {}", serverId, e.getMessage());
        }
    }

    private String classifyLogLevel(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("error") || lower.contains("fatal") || lower.contains("panic")) return "ERROR";
        if (lower.contains("warn")) return "WARN";
        return "INFO";
    }

    private List<LogEntryDTO> generateFallbackLogs() {
        return List.of();
    }

    @Transactional
    public void deleteServer(Long id) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + id));
        String ownerEmail = server.getUser() != null ? server.getUser().getEmail() : null;
        // Clean up SSH connections
        sshManager.removeConnection(server.getIp(), server.getSshPort() != null ? server.getSshPort() : 22);
        // Delete related containers and volumes
        containerRepository.deleteByServerId(id);
        volumeRepository.deleteByServerId(id);
        serverRepository.delete(server);
        if (ownerEmail != null) {
            cacheService.evict(CacheKeyBuilder.servers(ownerEmail));
        }
        cacheService.evict(CacheKeyBuilder.dashboardOverview());
    }

    // --- Docker Lifecycle ---

    public Map<String, Object> startContainer(Long serverId, String containerId) {
        Server server = getServerEntity(serverId);
        try {
            boolean success = dockerService.startContainer(server, containerId);
            if (success) {
                refreshContainers(server);
                return Map.of("success", true, "message", "容器已启动");
            }
            return Map.of("success", false, "message", "容器启动失败");
        } catch (Exception e) {
            return Map.of("success", false, "message", "操作失败: " + e.getMessage());
        }
    }

    public Map<String, Object> stopContainer(Long serverId, String containerId) {
        Server server = getServerEntity(serverId);
        try {
            boolean success = dockerService.stopContainer(server, containerId);
            if (success) {
                refreshContainers(server);
                return Map.of("success", true, "message", "容器已停止");
            }
            return Map.of("success", false, "message", "容器停止失败");
        } catch (Exception e) {
            return Map.of("success", false, "message", "操作失败: " + e.getMessage());
        }
    }

    public Map<String, Object> restartContainerAction(Long serverId, String containerId) {
        Server server = getServerEntity(serverId);
        try {
            boolean success = dockerService.restartContainer(server, containerId);
            if (success) {
                refreshContainers(server);
                return Map.of("success", true, "message", "容器已重启");
            }
            return Map.of("success", false, "message", "容器重启失败");
        } catch (Exception e) {
            return Map.of("success", false, "message", "操作失败: " + e.getMessage());
        }
    }

    public void removeContainer(Long serverId, String containerId, boolean force) {
        Server server = getServerEntity(serverId);
        try {
            dockerService.removeContainer(server, containerId, force);
            refreshContainers(server);
        } catch (Exception e) {
            throw new RuntimeException("删除容器失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> createContainer(Long serverId, CreateContainerRequest request) {
        Server server = getServerEntity(serverId);
        try {
            String containerId = dockerService.createContainer(server, request.image(), request.name(),
                    request.ports(), request.volumes(), request.env());
            refreshContainers(server);
            return Map.of("success", true, "containerId", containerId, "message", "容器创建成功");
        } catch (Exception e) {
            return Map.of("success", false, "message", "创建容器失败: " + e.getMessage());
        }
    }

    // --- Docker Image Management ---

    public List<Map<String, String>> getImages(Long serverId) {
        Server server = getServerEntity(serverId);
        try {
            return dockerService.listImages(server);
        } catch (Exception e) {
            log.warn("[SERVER_IMAGE] [server={}] Failed to list images: {}", serverId, e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> pullImage(Long serverId, String image) {
        Server server = getServerEntity(serverId);
        try {
            boolean success = dockerService.pullImage(server, image);
            return Map.of("success", success, "message", success ? "镜像拉取成功" : "镜像拉取失败");
        } catch (Exception e) {
            return Map.of("success", false, "message", "拉取失败: " + e.getMessage());
        }
    }

    public void removeImage(Long serverId, String imageId, boolean force) {
        Server server = getServerEntity(serverId);
        try {
            dockerService.removeImage(server, imageId, force);
        } catch (Exception e) {
            throw new RuntimeException("删除镜像失败: " + e.getMessage(), e);
        }
    }

    // --- Docker Network ---

    public List<String[]> getTopologyEdges(Long serverId) {
        Server server = getServerEntity(serverId);
        try {
            return dockerService.getTopologyEdges(server);
        } catch (Exception e) {
            log.warn("[SERVER_TOPOLOGY] [server={}] Failed to get topology edges: {}", serverId, e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, String>> getNetworks(Long serverId) {
        Server server = getServerEntity(serverId);
        try {
            return dockerService.listNetworks(server);
        } catch (Exception e) {
            log.warn("[SERVER_NETWORK] [server={}] Failed to list networks: {}", serverId, e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> scanEnvironment(Long serverId) {
        Server server = getServerEntity(serverId);
        try {
            SshConnection conn = sshManager.getConnection(server);
            String cmd = String.join(" && ",
                    "echo '===OS===' && uname -a",
                    "echo '===DISK===' && df -h /",
                    "echo '===MEMORY===' && free -h",
                    "echo '===UPTIME===' && uptime",
                    "echo '===DOCKER===' && (docker ps -a --format '{{.Names}}\\t{{.Image}}\\t{{.Status}}' 2>/dev/null || echo 'NOT_INSTALLED')",
                    "echo '===DOCKER_STATS===' && (docker stats --no-stream --format '{{.Name}}\\t{{.CPUPerc}}\\t{{.MemUsage}}' 2>/dev/null || echo 'N/A')",
                    "echo '===DB_PORTS===' && (ss -tlnp 2>/dev/null | grep -E ':(3306|5432|6379|27017)' || echo 'NONE')"
            );
            SshConnection.CommandResult result = conn.executeCommand(cmd);
            if (!result.isSuccess()) {
                return Map.of("success", false, "message", "扫描命令执行失败: " + result.stderr());
            }
            return Map.of("success", true, "data", parseScanOutput(result.stdout()));
        } catch (Exception e) {
            return Map.of("success", false, "message", "环境扫描失败: " + e.getMessage());
        }
    }

    private Map<String, Object> parseScanOutput(String output) {
        String os = extractSection(output, "OS");
        String disk = extractSection(output, "DISK");
        String memory = extractSection(output, "MEMORY");
        String uptime = extractSection(output, "UPTIME");
        String dockerRaw = extractSection(output, "DOCKER");
        String dockerStatsRaw = extractSection(output, "DOCKER_STATS");
        String dbPortsRaw = extractSection(output, "DB_PORTS");

        boolean dockerInstalled = !dockerRaw.contains("NOT_INSTALLED");
        List<Map<String, String>> containers = new ArrayList<>();
        if (dockerInstalled && !dockerRaw.isBlank()) {
            for (String line : dockerRaw.lines().toList()) {
                String[] parts = line.split("\t");
                if (parts.length >= 3) {
                    Map<String, String> stats = findContainerStats(parts[0], dockerStatsRaw);
                    containers.add(Map.of(
                            "name", parts[0],
                            "image", parts[1],
                            "status", parts[2],
                            "cpu", stats.getOrDefault("cpu", "N/A"),
                            "memory", stats.getOrDefault("memory", "N/A")
                    ));
                }
            }
        }

        List<Map<String, String>> databases = new ArrayList<>();
        if (!dbPortsRaw.contains("NONE") && !dbPortsRaw.isBlank()) {
            for (String line : dbPortsRaw.lines().toList()) {
                String type = detectDbType(line);
                if (type != null) databases.add(Map.of("type", type, "port", extractPort(line)));
            }
        }

        return Map.of(
                "os", os.isEmpty() ? "Unknown" : os.trim(),
                "disk", disk.isEmpty() ? "Unknown" : disk.trim(),
                "memory", memory.isEmpty() ? "Unknown" : memory.trim(),
                "uptime", uptime.isEmpty() ? "Unknown" : uptime.trim(),
                "dockerInstalled", dockerInstalled,
                "containers", containers,
                "databases", databases
        );
    }

    private String extractSection(String output, String section) {
        int start = output.indexOf("===" + section + "===");
        if (start < 0) return "";
        start = output.indexOf("\n", start);
        if (start < 0) return "";
        int end = output.indexOf("===", start + 1);
        if (end < 0) end = output.length();
        return output.substring(start + 1, end).strip();
    }

    private Map<String, String> findContainerStats(String name, String statsRaw) {
        for (String line : statsRaw.lines().toList()) {
            String[] p = line.split("\t");
            if (p.length >= 3 && p[0].equals(name)) {
                return Map.of("cpu", p[1], "memory", p[2]);
            }
        }
        return Map.of("cpu", "N/A", "memory", "N/A");
    }

    private String detectDbType(String line) {
        if (line.contains(":3306")) return "MySQL";
        if (line.contains(":5432")) return "PostgreSQL";
        if (line.contains(":6379")) return "Redis";
        if (line.contains(":27017")) return "MongoDB";
        return null;
    }

    private String extractPort(String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(":(\\d+)").matcher(line);
        return m.find() ? m.group(1) : "unknown";
    }

    @Transactional
    public int batchScan(List<Long> ids) {
        List<Server> servers = serverRepository.findAllById(ids);
        for (Server server : servers) {
            try {
                refreshContainers(server);
            } catch (Exception e) {
                log.error("[SERVER_BATCH_SCAN] [server={}] Batch scan failed: {}", server.getId(), e.getMessage());
            }
        }
        return servers.size();
    }

    /**
     * Generate a new SSH key pair and update the server's credentials.
     * The new private key is encrypted with AES-256-GCM before storage.
     * Returns the new public key for the user to install on the target server.
     */
    @Transactional
    public Map<String, Object> rotateKey(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        try {
            // Generate a new Ed25519 key pair (modern, secure, compact)
            java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("Ed25519");
            java.security.SecureRandom random = new java.security.SecureRandom();
            keyGen.initialize(256, random);
            java.security.KeyPair newKeyPair = keyGen.generateKeyPair();

            // Convert to OpenSSH format for storage
            net.i2p.crypto.eddsa.EdDSAPublicKey publicKey =
                    (net.i2p.crypto.eddsa.EdDSAPublicKey) newKeyPair.getPublic();
            net.i2p.crypto.eddsa.EdDSAPrivateKey privateKey =
                    (net.i2p.crypto.eddsa.EdDSAPrivateKey) newKeyPair.getPrivate();

            // Build OpenSSH format private key string
            String privateKeyPem = convertToOpenSSHPrivateKey(privateKey, publicKey);
            String publicKeyOpenSsh = convertToOpenSSHPublicKey(publicKey);

            // Encrypt the private key before storage
            String encryptedKey = credentialEncryptor.encrypt(privateKeyPem);

            // Update the server record
            server.setSshKeyEncrypted(encryptedKey);
            server.setSshAuthMethod("KEY");
            serverRepository.save(server);

            // Invalidate any cached SSH connections for this server
            sshManager.removeConnection(server.getIp(),
                    server.getSshPort() != null ? server.getSshPort() : 22);

            log.info("[SSH_KEY_ROTATE] [server={}] SSH key rotated. New key type: Ed25519", serverId);

            return Map.of(
                    "success", true,
                    "message", "SSH 密钥已轮换。请将以下公钥添加到目标服务器的 ~/.ssh/authorized_keys 中",
                    "publicKey", publicKeyOpenSsh,
                    "keyType", "Ed25519",
                    "fingerprint", java.security.MessageDigest.getInstance("SHA-256")
                            .digest(publicKey.getEncoded()).length + " bytes"
            );
        } catch (Exception e) {
            log.error("[SSH_KEY_ROTATE] [server={}] SSH key rotation failed: {}", serverId, e.getMessage(), e);
            throw new RuntimeException("SSH 密钥轮换失败: " + e.getMessage(), e);
        }
    }

    private String convertToOpenSSHPrivateKey(net.i2p.crypto.eddsa.EdDSAPrivateKey privateKey,
                                               net.i2p.crypto.eddsa.EdDSAPublicKey publicKey) throws Exception {
        // Manually construct OpenSSH Ed25519 private key format
        // This avoids dependency on OpenSSHKeyPairResourceWriter which isn't available in sshd 2.12.1

        // Extract raw 32-byte Ed25519 private seed from the EdDSA key
        byte[] rawPrivateKey = new byte[32];
        byte[] encodedPriv = privateKey.getEncoded();
        // EdDSA encoded private key contains the seed at a known offset
        System.arraycopy(encodedPriv, encodedPriv.length - 32, rawPrivateKey, 0, 32);

        // Extract raw 32-byte Ed25519 public key
        byte[] rawPublicKey = new byte[32];
        byte[] encodedPub = publicKey.getEncoded();
        System.arraycopy(encodedPub, encodedPub.length - 32, rawPublicKey, 0, 32);

        // Build OpenSSH key format manually using ByteArrayOutputStream
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();

        // Magic: "openssh-key-v1\0"
        bos.write("openssh-key-v1\0".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Cipher name: "none" (unencrypted)
        writeOpenSSHBlob(bos, "none".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // KDF name: "none"
        writeOpenSSHBlob(bos, "none".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // KDF options: empty
        writeOpenSSHBlob(bos, new byte[0]);

        // Number of keys: 1
        bos.write(0); bos.write(0); bos.write(0); bos.write(1);

        // Public key blob: "ssh-ed25519" || raw_pubkey
        java.io.ByteArrayOutputStream pubBlob = new java.io.ByteArrayOutputStream();
        writeOpenSSHBlob(pubBlob, "ssh-ed25519".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        writeOpenSSHBlob(pubBlob, rawPublicKey);
        writeOpenSSHBlob(bos, pubBlob.toByteArray());

        // Private key section (with padding)
        java.io.ByteArrayOutputStream privSection = new java.io.ByteArrayOutputStream();
        // Check int: random 4 bytes (use 0xDEADBEEF as placeholder for unencrypted)
        privSection.write(0xDE); privSection.write(0xAD); privSection.write(0xBE); privSection.write(0xEF);
        // Key type: "ssh-ed25519"
        writeOpenSSHBlob(privSection, "ssh-ed25519".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // Public key (32 bytes)
        writeOpenSSHBlob(privSection, rawPublicKey);
        // Private key (64 bytes: 32 seed + 32 public)
        byte[] fullPrivateKey = new byte[64];
        System.arraycopy(rawPrivateKey, 0, fullPrivateKey, 0, 32);
        System.arraycopy(rawPublicKey, 0, fullPrivateKey, 32, 32);
        writeOpenSSHBlob(privSection, fullPrivateKey);
        // Comment
        writeOpenSSHBlob(privSection, "chronovault@rotated".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // Padding: pad to block size (8 bytes) with incrementing bytes 1,2,3...
        int pad = 1;
        while (privSection.size() % 8 != 0) {
            privSection.write(pad++);
        }
        writeOpenSSHBlob(bos, privSection.toByteArray());

        // Base64 encode and wrap at 70 chars
        String b64 = java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN OPENSSH PRIVATE KEY-----\n");
        for (int i = 0; i < b64.length(); i += 70) {
            sb.append(b64, i, Math.min(i + 70, b64.length()));
            sb.append("\n");
        }
        sb.append("-----END OPENSSH PRIVATE KEY-----\n");
        return sb.toString();
    }

    /** Write a length-prefixed blob to the output stream */
    private void writeOpenSSHBlob(java.io.OutputStream os, byte[] data) throws java.io.IOException {
        os.write((data.length >> 24) & 0xFF);
        os.write((data.length >> 16) & 0xFF);
        os.write((data.length >> 8) & 0xFF);
        os.write(data.length & 0xFF);
        os.write(data);
    }

    private String convertToOpenSSHPublicKey(net.i2p.crypto.eddsa.EdDSAPublicKey publicKey) {
        // Build OpenSSH public key format: ssh-ed25519 <base64> <comment>
        byte[] encoded = publicKey.getEncoded();
        // Ed25519 public key is 32 bytes raw, but getEncoded() returns the full X509 format
        // Extract the raw 32-byte public key from the X509 encoding
        // The raw key is at offset 12 in the X509 encoding for Ed25519
        byte[] rawKey = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, rawKey, 0, 32);

        String base64Key = java.util.Base64.getEncoder().encodeToString(rawKey);
        return "ssh-ed25519 " + base64Key + " chronovault@rotated";
    }

    private Server getServerEntity(Long id) {
        return serverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + id));
    }

    /**
     * Get live state of a server without creating a snapshot.
     * Connects via SSH, runs state collectors, and returns the state.json.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getLiveState(Long serverId) {
        Server server = getServerEntity(serverId);
        try {
            SshConnection conn = sshManager.getConnection(server);
            String stateJson = stateCollectionService.collectStateViaSsh(conn);
            if (stateJson == null || stateJson.isBlank()) {
                return Map.of("error", "State collection returned empty", "serverId", serverId);
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> state = (Map<String, Object>) mapper.readValue(stateJson, Object.class);
            state.put("serverId", serverId);
            state.put("serverName", server.getName());
            state.put("live", true);
            return state;
        } catch (java.io.IOException e) {
            log.error("[LIVE_STATE] Failed to collect live state for server {}: {}", serverId, e.getMessage());
            return Map.of("error", "Failed to connect or collect: " + e.getMessage(), "serverId", serverId, "live", true);
        } catch (Exception e) {
            log.error("[LIVE_STATE] Failed to collect live state for server {}: {}", serverId, e.getMessage());
            return Map.of("error", e.getMessage(), "serverId", serverId, "live", true);
        }
    }
}
