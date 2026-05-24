package com.chronovault.service;

import com.chronovault.dto.server.*;
import com.chronovault.docker.DockerOperationService;
import com.chronovault.entity.*;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.*;
import com.chronovault.security.CredentialEncryptor;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserService userService;
    private final DockerOperationService dockerService;
    private final SshConnectionManager sshManager;
    private final CredentialEncryptor credentialEncryptor;

    public List<ServerDTO> getServers(String email) {
        User user = userService.getByEmail(email);
        return serverRepository.findByUserId(user.getId()).stream()
                .map(ServerDTO::from)
                .toList();
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
            log.warn("SSH probe failed for {}: {}", ip, e.getMessage());
        }

        // Trigger async container/volume scan
        try {
            refreshContainers(server);
            refreshVolumes(server);
        } catch (Exception e) {
            log.warn("Initial scan failed for {}: {}", ip, e.getMessage());
        }

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
            log.warn("Failed to refresh containers: {}", e.getMessage());
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
                log.warn("Failed to refresh volumes: {}", e.getMessage());
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
            log.warn("Failed to get real logs: {}", e.getMessage());
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
            log.warn("Failed to refresh containers for {}: {}", server.getIp(), e.getMessage());
        }
    }

    @Transactional
    public void refreshVolumes(Server server) {
        try {
            List<Volume> volumes = dockerService.listVolumes(server);
            volumeRepository.deleteByServerId(server.getId());
            volumeRepository.saveAll(volumes);
        } catch (Exception e) {
            log.warn("Failed to refresh volumes for {}: {}", server.getIp(), e.getMessage());
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
            log.info("Cleared Docker container logs on {}", server.getIp());
        } catch (Exception e) {
            log.warn("Failed to clear logs on {}: {}", server.getIp(), e.getMessage());
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
        // Clean up SSH connections
        sshManager.removeConnection(server.getIp(), server.getSshPort() != null ? server.getSshPort() : 22);
        // Delete related containers and volumes
        containerRepository.deleteByServerId(id);
        volumeRepository.deleteByServerId(id);
        serverRepository.delete(server);
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
            log.warn("Failed to list images on {}: {}", server.getIp(), e.getMessage());
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
            log.warn("Failed to get topology edges: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, String>> getNetworks(Long serverId) {
        Server server = getServerEntity(serverId);
        try {
            return dockerService.listNetworks(server);
        } catch (Exception e) {
            log.warn("Failed to list networks on {}: {}", server.getIp(), e.getMessage());
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
                log.error("Batch scan failed for server {}: {}", server.getId(), e.getMessage());
            }
        }
        return servers.size();
    }

    private Server getServerEntity(Long id) {
        return serverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + id));
    }
}
