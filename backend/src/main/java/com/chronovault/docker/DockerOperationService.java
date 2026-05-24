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
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerOperationService {

    private final SshConnectionManager sshManager;
    private final ObjectMapper objectMapper;

    // Injection-safe patterns for Docker identifiers
    private static final Pattern CONTAINER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.\\-]{0,127}$");
    private static final Pattern IMAGE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.\\-/:]{0,255}$");
    private static final Pattern VOLUME_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.\\-]{0,255}$");

    private String sanitizeId(String id, String type) {
        if (id == null || !CONTAINER_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("无效的" + type + "标识符: " + id);
        }
        return id;
    }

    public List<Container> listContainers(Server server) throws Exception {
        SshConnection conn = sshManager.getConnection(server);
        // Combined script: container list + stats + network membership
        String script = """
                echo '===CONTAINERS==='
                docker ps -a --format '{{json .}}'
                echo '===STATS==='
                docker stats --no-stream --format '{{json .}}'
                echo '===NETWORKS==='
                docker network ls --filter driver=bridge --format '{{.Name}}'
                """;
        SshConnection.CommandResult result = conn.executeCommand("sh -c " + escapeShell(script));

        if (!result.isSuccess()) {
            log.warn("docker ps/stats failed on {}: {}", server.getIp(), result.stderr());
            return Collections.emptyList();
        }

        String output = result.stdout();
        String[] parts = output.split("===STATS===");
        String containersPart = parts.length > 0 ? parts[0] : "";
        String[] statsAndNetworks = parts.length > 1 ? parts[1].split("===NETWORKS===") : new String[]{"", ""};
        String statsPart = statsAndNetworks[0];
        String networksPart = statsAndNetworks.length > 1 ? statsAndNetworks[1] : "";

        // Parse stats first
        Map<String, Map<String, String>> statsMap = new java.util.HashMap<>();
        for (String line : statsPart.lines().toList()) {
            if (line.isBlank() || line.startsWith("===STATS===")) continue;
            try {
                Map<String, String> data = objectMapper.readValue(line.trim(), new TypeReference<>() {});
                String name = data.get("Name");
                if (name != null) {
                    statsMap.put(name, data);
                }
            } catch (Exception e) {
                log.debug("Failed to parse stats line: {}", line);
            }
        }

        // Discover network memberships for topology
        Map<String, Set<String>> networkMembers = new java.util.HashMap<>();
        for (String networkName : networksPart.lines().toList()) {
            String netName = networkName.trim();
            if (netName.isBlank()) continue;
            try {
                SshConnection.CommandResult netResult = conn.executeCommand(
                        "docker network inspect " + netName + " --format '{{range .Containers}}{{.Name}} {{end}}'");
                if (netResult.isSuccess() && !netResult.stdout().isBlank()) {
                    Set<String> members = new java.util.HashSet<>();
                    for (String member : netResult.stdout().trim().split("\\s+")) {
                        if (!member.isBlank()) members.add(member);
                    }
                    if (members.size() > 1) { // Only networks with 2+ containers create edges
                        networkMembers.put(netName, members);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to inspect network {}: {}", netName, e.getMessage());
            }
        }

        // Parse containers and merge stats
        List<Container> containers = new ArrayList<>();
        for (String line : containersPart.lines().toList()) {
            if (line.isBlank() || line.startsWith("===CONTAINERS===")) continue;
            try {
                Map<String, Object> data = objectMapper.readValue(line.trim(), new TypeReference<>() {});
                String name = getString(data, "Names");
                Container c = Container.builder()
                        .server(server)
                        .name(name)
                        .type(mapContainerType(getString(data, "Image")))
                        .status(mapContainerStatus(getString(data, "State")))
                        .build();

                // Merge real-time stats
                Map<String, String> stats = statsMap.get(name);
                if (stats != null) {
                    c.setCpuPercent(parsePercent(stats.get("CPUPerc")));
                    c.setMemoryPercent(parsePercent(stats.get("MemPerc")));
                    c.setMemoryMb(parseMemoryMb(stats.get("MemUsage")));
                    c.setDiskIo(stats.get("BlockIO"));
                }

                // Determine which networks this container belongs to
                List<String> myNetworks = new ArrayList<>();
                for (var entry : networkMembers.entrySet()) {
                    if (entry.getValue().contains(name)) {
                        myNetworks.add(entry.getKey());
                    }
                }
                c.setNetworks(String.join(",", myNetworks));

                containers.add(c);
            } catch (Exception e) {
                log.debug("Failed to parse container line: {}", line);
            }
        }
        return containers;
    }

    /**
     * Returns topology edges: containers sharing a Docker network are connected.
     * Returns list of [containerA, containerB, networkName] triples.
     */
    public List<String[]> getTopologyEdges(Server server) throws Exception {
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand(
                "docker network ls --filter driver=bridge --format '{{.Name}}'");
        if (!result.isSuccess()) return Collections.emptyList();

        Map<String, Set<String>> networkMembers = new java.util.HashMap<>();
        for (String networkName : result.stdout().lines().toList()) {
            String netName = networkName.trim();
            if (netName.isBlank() || netName.equals("bridge") || netName.equals("host") || netName.equals("none")) continue;
            try {
                SshConnection.CommandResult netResult = conn.executeCommand(
                        "docker network inspect " + netName + " --format '{{range .Containers}}{{.Name}} {{end}}'");
                if (netResult.isSuccess() && !netResult.stdout().isBlank()) {
                    Set<String> members = new java.util.HashSet<>();
                    for (String member : netResult.stdout().trim().split("\\s+")) {
                        if (!member.isBlank()) members.add(member);
                    }
                    if (members.size() > 1) {
                        networkMembers.put(netName, members);
                    }
                }
            } catch (Exception ignored) {}
        }

        List<String[]> edges = new ArrayList<>();
        for (var entry : networkMembers.entrySet()) {
            List<String> members = new ArrayList<>(entry.getValue());
            for (int i = 0; i < members.size(); i++) {
                for (int j = i + 1; j < members.size(); j++) {
                    edges.add(new String[]{members.get(i), members.get(j), entry.getKey()});
                }
            }
        }
        return edges;
    }

    private String escapeShell(String script) {
        // Escape for sh -c '...'
        return "'" + script.replace("'", "'\\''") + "'";
    }

    private Double parsePercent(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try {
            return Double.parseDouble(value.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Long parseMemoryMb(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            // Format: "10.5MiB / 1GiB" - take the first part
            String used = value.split("/")[0].trim();
            double num = Double.parseDouble(used.replaceAll("[^0-9.]", ""));
            if (used.toUpperCase().contains("GIB") || used.toUpperCase().contains("GB")) {
                return (long) (num * 1024);
            }
            return (long) num; // Assume MiB
        } catch (Exception e) {
            return 0L;
        }
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

    // --- Docker Lifecycle Management ---

    public boolean startContainer(Server server, String containerId) throws Exception {
        sanitizeId(containerId, "容器");
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand("docker start " + containerId);
        if (result.isSuccess()) {
            log.info("Container {} started on {}", containerId, server.getIp());
            return true;
        }
        log.warn("Failed to start container {} on {}: {}", containerId, server.getIp(), result.stderr());
        return false;
    }

    public boolean stopContainer(Server server, String containerId) throws Exception {
        sanitizeId(containerId, "容器");
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand("docker stop " + containerId);
        if (result.isSuccess()) {
            log.info("Container {} stopped on {}", containerId, server.getIp());
            return true;
        }
        log.warn("Failed to stop container {} on {}: {}", containerId, server.getIp(), result.stderr());
        return false;
    }

    public boolean removeContainer(Server server, String containerId, boolean force) throws Exception {
        sanitizeId(containerId, "容器");
        SshConnection conn = sshManager.getConnection(server);
        String cmd = force ? "docker rm -f " + containerId : "docker rm " + containerId;
        SshConnection.CommandResult result = conn.executeCommand(cmd);
        if (result.isSuccess()) {
            log.info("Container {} removed from {}", containerId, server.getIp());
            return true;
        }
        log.warn("Failed to remove container {} on {}: {}", containerId, server.getIp(), result.stderr());
        return false;
    }

    public String createContainer(Server server, String image, String name, String ports, String volumes, String env) throws Exception {
        if (!IMAGE_NAME_PATTERN.matcher(image).matches()) {
            throw new IllegalArgumentException("无效的镜像名称: " + image);
        }
        if (name != null && !name.isBlank() && !CONTAINER_ID_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("无效的容器名称: " + name);
        }

        StringBuilder cmd = new StringBuilder("docker run -d");
        if (name != null && !name.isBlank()) {
            cmd.append(" --name ").append(name);
        }
        if (ports != null && !ports.isBlank()) {
            // Validate port mappings: "8080:80,443:443"
            for (String portMap : ports.split(",")) {
                String trimmed = portMap.trim();
                if (!trimmed.matches("^\\d{1,5}:\\d{1,5}$")) {
                    throw new IllegalArgumentException("无效的端口映射: " + trimmed);
                }
                cmd.append(" -p ").append(trimmed);
            }
        }
        if (volumes != null && !volumes.isBlank()) {
            for (String volMap : volumes.split(",")) {
                String trimmed = volMap.trim();
                // Allow host:container format, validate paths
                if (!trimmed.matches("^[a-zA-Z0-9_/\\-.]+:[a-zA-Z0-9_/\\-.]+$")) {
                    throw new IllegalArgumentException("无效的卷映射: " + trimmed);
                }
                cmd.append(" -v ").append(trimmed);
            }
        }
        if (env != null && !env.isBlank()) {
            for (String e : env.split(",")) {
                String trimmed = e.trim();
                if (!trimmed.matches("^[a-zA-Z_][a-zA-Z0-9_]*=.+$")) {
                    throw new IllegalArgumentException("无效的环境变量: " + trimmed);
                }
                cmd.append(" -e ").append(trimmed);
            }
        }
        cmd.append(" ").append(image);

        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand(cmd.toString());
        if (result.isSuccess()) {
            String containerId = result.stdout().trim();
            log.info("Container {} created from {} on {}", containerId, image, server.getIp());
            return containerId;
        }
        throw new RuntimeException("创建容器失败: " + result.stderr());
    }

    // --- Docker Image Management ---

    public List<Map<String, String>> listImages(Server server) throws Exception {
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand(
                "docker images --format '{{json .}}'");
        if (!result.isSuccess()) {
            return Collections.emptyList();
        }
        List<Map<String, String>> images = new ArrayList<>();
        for (String line : result.stdout().lines().toList()) {
            if (line.isBlank()) continue;
            try {
                Map<String, Object> data = objectMapper.readValue(line, new TypeReference<>() {});
                Map<String, String> img = new LinkedHashMap<>();
                img.put("repository", getString(data, "Repository"));
                img.put("tag", getString(data, "Tag"));
                img.put("id", getString(data, "ID"));
                img.put("size", getString(data, "Size"));
                img.put("createdAt", getString(data, "CreatedAt"));
                images.add(img);
            } catch (Exception e) {
                log.debug("Failed to parse image line: {}", line);
            }
        }
        return images;
    }

    public boolean pullImage(Server server, String image) throws Exception {
        if (!IMAGE_NAME_PATTERN.matcher(image).matches()) {
            throw new IllegalArgumentException("无效的镜像名称: " + image);
        }
        SshConnection conn = sshManager.getConnection(server);
        // Pull with 5-minute timeout
        SshConnection.CommandResult result = conn.executeCommand(
                "docker pull " + image, java.time.Duration.ofMinutes(5));
        if (result.isSuccess()) {
            log.info("Image {} pulled on {}", image, server.getIp());
            return true;
        }
        log.warn("Failed to pull image {} on {}: {}", image, server.getIp(), result.stderr());
        return false;
    }

    public boolean removeImage(Server server, String imageId, boolean force) throws Exception {
        sanitizeId(imageId, "镜像");
        SshConnection conn = sshManager.getConnection(server);
        String cmd = force ? "docker rmi -f " + imageId : "docker rmi " + imageId;
        SshConnection.CommandResult result = conn.executeCommand(cmd);
        if (result.isSuccess()) {
            log.info("Image {} removed from {}", imageId, server.getIp());
            return true;
        }
        log.warn("Failed to remove image {} on {}: {}", imageId, server.getIp(), result.stderr());
        return false;
    }

    // --- Docker Network Management ---

    public List<Map<String, String>> listNetworks(Server server) throws Exception {
        SshConnection conn = sshManager.getConnection(server);
        SshConnection.CommandResult result = conn.executeCommand(
                "docker network ls --format '{{json .}}'");
        if (!result.isSuccess()) {
            return Collections.emptyList();
        }
        List<Map<String, String>> networks = new ArrayList<>();
        for (String line : result.stdout().lines().toList()) {
            if (line.isBlank()) continue;
            try {
                Map<String, Object> data = objectMapper.readValue(line, new TypeReference<>() {});
                Map<String, String> net = new LinkedHashMap<>();
                net.put("id", getString(data, "ID"));
                net.put("name", getString(data, "Name"));
                net.put("driver", getString(data, "Driver"));
                net.put("scope", getString(data, "Scope"));
                networks.add(net);
            } catch (Exception e) {
                log.debug("Failed to parse network line: {}", line);
            }
        }
        return networks;
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
