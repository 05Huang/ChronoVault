package com.chronovault.docker;

import com.chronovault.entity.Container;
import com.chronovault.entity.Server;
import com.chronovault.entity.Volume;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerOperationService {

    private final SshConnectionManager sshManager;
    private final ObjectMapper objectMapper;

    public List<Container> listContainers(Server server) throws Exception {
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand(
                "docker ps -a --format '{{json .}}'");

        if (!result.isSuccess()) {
            log.warn("docker ps failed on {}: {}", server.getIp(), result.stderr());
            return Collections.emptyList();
        }

        List<Container> containers = new ArrayList<>();
        for (String line : result.stdout().lines().toList()) {
            if (line.isBlank()) continue;
            try {
                Map<String, Object> data = objectMapper.readValue(line, new TypeReference<>() {});
                Container c = Container.builder()
                        .server(server)
                        .name(getString(data, "Names"))
                        .type(mapContainerType(getString(data, "Image")))
                        .status(mapContainerStatus(getString(data, "State")))
                        .build();
                containers.add(c);
            } catch (Exception e) {
                log.debug("Failed to parse container line: {}", line);
            }
        }
        return containers;
    }

    public String getContainerLogs(Server server, String containerName, int tail) throws Exception {
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand(
                "docker logs --tail " + tail + " " + containerName + " 2>&1");
        return result.isSuccess() ? result.stdout() : "Error: " + result.stderr();
    }

    public boolean restartContainer(Server server, String containerName) throws Exception {
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand(
                "docker restart " + containerName);
        return result.isSuccess();
    }

    public List<Volume> listVolumes(Server server) throws Exception {
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand(
                "docker volume ls --format '{{json .}}'");

        if (!result.isSuccess()) {
            return Collections.emptyList();
        }

        List<Volume> volumes = new ArrayList<>();
        for (String line : result.stdout().lines().toList()) {
            if (line.isBlank()) continue;
            try {
                Map<String, Object> data = objectMapper.readValue(line, new TypeReference<>() {});
                Volume v = Volume.builder()
                        .server(server)
                        .name(getString(data, "Name"))
                        .containerPath(getString(data, "Mountpoint"))
                        .status("ACTIVE")
                        .build();
                volumes.add(v);
            } catch (Exception e) {
                log.debug("Failed to parse volume line: {}", line);
            }
        }
        return volumes;
    }

    public Map<String, Object> inspectContainer(Server server, String containerId) throws Exception {
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand(
                "docker inspect " + containerId);
        if (!result.isSuccess()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> list = objectMapper.readValue(result.stdout(), new TypeReference<>() {});
        return list.isEmpty() ? Collections.emptyMap() : list.get(0);
    }

    public String getServerSystemInfo(Server server) throws Exception {
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand(
                "uname -a && df -h / && free -h && uptime");
        return result.isSuccess() ? result.stdout() : "";
    }

    private String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : "";
    }

    private Container.ContainerType mapContainerType(String image) {
        String lower = image.toLowerCase();
        if (lower.contains("nginx") || lower.contains("apache") || lower.contains("caddy") || lower.contains("httpd"))
            return Container.ContainerType.NGINX;
        if (lower.contains("mysql") || lower.contains("postgres") || lower.contains("mongo"))
            return Container.ContainerType.MYSQL;
        if (lower.contains("redis"))
            return Container.ContainerType.REDIS;
        return Container.ContainerType.API;
    }

    private Container.ContainerStatus mapContainerStatus(String state) {
        if (state == null) return Container.ContainerStatus.STOPPED;
        return switch (state.toLowerCase()) {
            case "running" -> Container.ContainerStatus.RUNNING;
            case "paused" -> Container.ContainerStatus.PAUSED;
            default -> Container.ContainerStatus.STOPPED;
        };
    }
}
