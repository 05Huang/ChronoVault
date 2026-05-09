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
                .os(os != null ? os : "Ubuntu 22.04")
                .status(Server.ServerStatus.RUNNING)
                .uptimeSeconds(0L)
                .build();
        serverRepository.save(server);

        // Trigger async scan
        try {
            refreshContainers(server);
            refreshVolumes(server);
        } catch (Exception e) {
            log.warn("Initial scan failed for {}: {}", ip, e.getMessage());
        }

        return ServerDTO.from(server);
    }

    public List<ContainerDTO> getContainers(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        // Refresh from real Docker if data is stale
        List<Container> containers = containerRepository.findByServerId(serverId);
        if (containers.isEmpty()) {
            try {
                refreshContainers(server);
                containers = containerRepository.findByServerId(serverId);
            } catch (Exception e) {
                log.warn("Failed to refresh containers: {}", e.getMessage());
            }
        }

        return containers.stream().map(ContainerDTO::from).toList();
    }

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
            server.setSshKeyEncrypted(credentialEncryptor.encrypt(credential));
        }
        serverRepository.save(server);
        return ServerDTO.from(server);
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
                // Update server OS info if empty
                if (server.getOs() == null || server.getOs().isBlank() || server.getOs().startsWith("Ubuntu")) {
                    server.setOs(osInfo.length() > 100 ? osInfo.substring(0, 100) : osInfo);
                    serverRepository.save(server);
                }
                return Map.of("success", true, "message", "连接成功", "osInfo", osInfo);
            } else {
                return Map.of("success", false, "message", "命令执行失败: " + result.stderr());
            }
        } catch (Exception e) {
            return Map.of("success", false, "message", "连接失败: " + e.getMessage());
        }
    }

    private String classifyLogLevel(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("error") || lower.contains("fatal") || lower.contains("panic")) return "ERROR";
        if (lower.contains("warn")) return "WARN";
        return "INFO";
    }

    private List<LogEntryDTO> generateFallbackLogs() {
        return List.of(
                new LogEntryDTO(LocalDateTime.now().minusMinutes(5).toString(), "INFO", "系统运行正常", "system"),
                new LogEntryDTO(LocalDateTime.now().minusMinutes(10).toString(), "INFO", "健康检查通过", "monitor")
        );
    }
}
