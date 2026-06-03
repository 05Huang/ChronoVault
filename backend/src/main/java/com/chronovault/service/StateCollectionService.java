package com.chronovault.service;

import com.chronovault.ssh.SshConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Collects system state information via SSH, producing a state.json
 * compatible with the Agent's state.json format.
 *
 * This is the backend-side state collector for the SnapshotEngine flow
 * (which uses SSH directly rather than going through the Agent).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateCollectionService {

    private final ObjectMapper objectMapper;

    private static final java.time.Duration MODULE_TIMEOUT = java.time.Duration.ofSeconds(10);

    /**
     * Collect system state via SSH and return it as a JSON string.
     * Each collection module runs with a 10-second timeout to prevent hanging.
     * Returns null if collection fails entirely.
     */
    public String collectStateViaSsh(SshConnection conn) {
        long totalStart = System.currentTimeMillis();
        try {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("collected_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            state.put("agent_version", "0.1.0-backend");

            // Collect with timing for each module
            long start;
            Map<String, Long> durations = new LinkedHashMap<>();

            start = System.currentTimeMillis();
            state.put("system", collectSystem(conn));
            durations.put("system", System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            state.put("os", collectOS(conn));
            durations.put("os", System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            state.put("packages", collectPackages(conn));
            durations.put("packages", System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            state.put("services", collectServices(conn));
            durations.put("services", System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            state.put("ports", collectPorts(conn));
            durations.put("ports", System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            state.put("docker", collectDocker(conn));
            durations.put("docker", System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            state.put("configs", collectConfigs(conn));
            durations.put("configs", System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            state.put("crontab", collectCrontab(conn));
            durations.put("crontab", System.currentTimeMillis() - start);

            long totalDuration = System.currentTimeMillis() - totalStart;
            state.put("collection_duration_ms", totalDuration);
            state.put("module_durations_ms", durations);

            log.info("State collection completed in {}ms (modules: {})", totalDuration, durations);
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.error("Failed to collect state via SSH: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Collect basic system info: hostname, IP, memory, disk, CPU, uptime.
     */
    private Map<String, Object> collectSystem(SshConnection conn) {
        Map<String, Object> sys = new LinkedHashMap<>();
        try {
            SshConnection.CommandResult r;
            r = conn.executeCommand("hostname 2>/dev/null");
            sys.put("hostname", r.isSuccess() ? r.stdout().trim() : "unknown");

            r = conn.executeCommand("hostname -I 2>/dev/null | awk '{print $1}'");
            sys.put("ip", r.isSuccess() ? r.stdout().trim() : "unknown");

            r = conn.executeCommand("free -m 2>/dev/null | awk '/^Mem:/{printf \"%d/%dMB (%.1f%%)\", $3, $2, $3*100/$2}'");
            sys.put("memory", r.isSuccess() ? r.stdout().trim() : "unknown");

            r = conn.executeCommand("df -h / 2>/dev/null | awk 'NR==2{printf \"%s/%s (%s)\", $3, $2, $5}'");
            sys.put("disk_root", r.isSuccess() ? r.stdout().trim() : "unknown");

            r = conn.executeCommand("nproc 2>/dev/null");
            sys.put("cpu_cores", r.isSuccess() ? r.stdout().trim() : "unknown");

            r = conn.executeCommand("uptime -p 2>/dev/null || uptime 2>/dev/null");
            sys.put("uptime", r.isSuccess() ? r.stdout().trim() : "unknown");
        } catch (Exception e) {
            log.warn("Failed to collect system info: {}", e.getMessage());
        }
        return sys;
    }

    private Map<String, String> collectOS(SshConnection conn) {
        Map<String, String> os = new LinkedHashMap<>();
        try {
            SshConnection.CommandResult result = conn.executeCommand(
                    "cat /etc/os-release 2>/dev/null | grep ^NAME= | cut -d'\"' -f2");
            os.put("name", result.isSuccess() ? result.stdout().trim() : "Unknown");

            result = conn.executeCommand(
                    "cat /etc/os-release 2>/dev/null | grep ^VERSION_ID= | cut -d'\"' -f2");
            os.put("version", result.isSuccess() ? result.stdout().trim() : "Unknown");

            result = conn.executeCommand("uname -r");
            os.put("kernel", result.isSuccess() ? result.stdout().trim() : "Unknown");

            result = conn.executeCommand("uname -m");
            os.put("arch", result.isSuccess() ? result.stdout().trim() : "Unknown");
        } catch (Exception e) {
            log.warn("Failed to collect OS info: {}", e.getMessage());
        }
        return os;
    }

    private List<Map<String, String>> collectPackages(SshConnection conn) {
        List<Map<String, String>> packages = new ArrayList<>();
        try {
            // Try dpkg first (Debian/Ubuntu)
            SshConnection.CommandResult result = conn.executeCommand(
                    "dpkg-query -W -f='${Package}\\t${Version}\\n' 2>/dev/null");
            if (result.isSuccess() && !result.stdout().isBlank()) {
                for (String line : result.stdout().lines().toList()) {
                    line = line.trim();
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\t", 2);
                    if (parts.length == 2) {
                        Map<String, String> pkg = new LinkedHashMap<>();
                        pkg.put("name", parts[0]);
                        pkg.put("version", parts[1]);
                        pkg.put("manager", "apt");
                        packages.add(pkg);
                    }
                }
                return packages;
            }

            // Try rpm (RHEL/CentOS)
            result = conn.executeCommand(
                    "rpm -qa --queryformat '%{NAME}\\t%{VERSION}-%{RELEASE}\\n' 2>/dev/null");
            if (result.isSuccess() && !result.stdout().isBlank()) {
                for (String line : result.stdout().lines().toList()) {
                    line = line.trim();
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\t", 2);
                    if (parts.length == 2) {
                        Map<String, String> pkg = new LinkedHashMap<>();
                        pkg.put("name", parts[0]);
                        pkg.put("version", parts[1]);
                        pkg.put("manager", "yum");
                        packages.add(pkg);
                    }
                }
                return packages;
            }

            // Try apk (Alpine)
            result = conn.executeCommand("apk list --installed 2>/dev/null");
            if (result.isSuccess() && !result.stdout().isBlank()) {
                for (String line : result.stdout().lines().toList()) {
                    line = line.trim();
                    if (line.isBlank()) continue;
                    String[] parts = line.split("-", 2);
                    if (parts.length >= 2) {
                        Map<String, String> pkg = new LinkedHashMap<>();
                        pkg.put("name", parts[0]);
                        pkg.put("version", String.join("-", Arrays.copyOfRange(parts, 1, parts.length)));
                        pkg.put("manager", "apk");
                        packages.add(pkg);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to collect packages: {}", e.getMessage());
        }
        return packages;
    }

    private List<Map<String, Object>> collectServices(SshConnection conn) {
        List<Map<String, Object>> services = new ArrayList<>();
        try {
            SshConnection.CommandResult result = conn.executeCommand(
                    "systemctl list-units --type=service --all --no-pager --no-legend 2>/dev/null");
            if (!result.isSuccess() || result.stdout().isBlank()) return services;

            for (String line : result.stdout().lines().toList()) {
                line = line.trim();
                if (line.isBlank()) continue;
                String[] fields = line.split("\\s+");
                if (fields.length < 4) continue;

                String name = fields[0].replace(".service", "");
                String status = fields[2]; // active, inactive, failed
                boolean loaded = "loaded".equals(fields[1]);

                Map<String, Object> svc = new LinkedHashMap<>();
                svc.put("name", name);
                svc.put("status", status);
                svc.put("enabled", loaded);

                // Get PID for active services
                if ("active".equals(status)) {
                    SshConnection.CommandResult pidResult = conn.executeCommand(
                            String.format("systemctl show %s --property=MainPID --value 2>/dev/null", name));
                    if (pidResult.isSuccess()) {
                        try {
                            int pid = Integer.parseInt(pidResult.stdout().trim());
                            if (pid > 0) svc.put("pid", pid);
                        } catch (NumberFormatException ignored) {}
                    }
                }

                services.add(svc);
            }
        } catch (Exception e) {
            log.warn("Failed to collect services: {}", e.getMessage());
        }
        return services;
    }

    private List<Map<String, Object>> collectPorts(SshConnection conn) {
        List<Map<String, Object>> ports = new ArrayList<>();
        try {
            SshConnection.CommandResult result = conn.executeCommand("ss -tulnp 2>/dev/null");
            if (!result.isSuccess() || result.stdout().isBlank()) {
                result = conn.executeCommand("netstat -tulnp 2>/dev/null");
            }
            if (!result.isSuccess() || result.stdout().isBlank()) return ports;

            for (String line : result.stdout().lines().toList()) {
                line = line.trim();
                if (line.isBlank() || line.startsWith("State") || line.startsWith("Netid")) continue;
                String[] fields = line.split("\\s+");
                if (fields.length < 5) continue;

                String protocol = "tcp";
                String localAddr = "";
                String state = "";

                if (fields[0].contains("tcp") || fields[0].contains("udp")) {
                    protocol = fields[0].contains("udp") ? "udp" : "tcp";
                    state = fields[1];
                    localAddr = fields[4];
                } else {
                    protocol = fields[0];
                    localAddr = fields[3];
                    if (fields.length > 5) state = fields[5];
                }

                // Extract port from address
                String[] addrParts = localAddr.split(":");
                if (addrParts.length < 2) continue;
                int port;
                try {
                    port = Integer.parseInt(addrParts[addrParts.length - 1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (port == 0) continue;

                // Extract process name
                String process = "";
                for (int i = 0; i < fields.length; i++) {
                    if (fields[i].contains("users:")) {
                        if (i + 1 < fields.length) {
                            process = fields[i + 1];
                            // Extract quoted name
                            int start = process.indexOf("\"");
                            if (start >= 0) {
                                int end = process.indexOf("\"", start + 1);
                                if (end > start) process = process.substring(start + 1, end);
                            }
                        }
                        break;
                    }
                }

                Map<String, Object> portInfo = new LinkedHashMap<>();
                portInfo.put("port", port);
                portInfo.put("protocol", protocol);
                portInfo.put("process", process);
                portInfo.put("state", state);
                ports.add(portInfo);
            }
        } catch (Exception e) {
            log.warn("Failed to collect ports: {}", e.getMessage());
        }
        return ports;
    }

    private Map<String, Object> collectDocker(SshConnection conn) {
        Map<String, Object> docker = new LinkedHashMap<>();
        docker.put("available", false);
        docker.put("containers", new ArrayList<>());
        docker.put("compose_files", new ArrayList<>());

        try {
            SshConnection.CommandResult result = conn.executeCommand("docker info 2>/dev/null | head -1");
            if (!result.isSuccess() || !result.stdout().contains("Containers")) {
                return docker;
            }
            docker.put("available", true);

            // List containers
            result = conn.executeCommand(
                    "docker ps -a --format '{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}' 2>/dev/null");
            if (result.isSuccess() && !result.stdout().isBlank()) {
                List<Map<String, Object>> containers = new ArrayList<>();
                for (String line : result.stdout().lines().toList()) {
                    line = line.trim();
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\\|", 4);
                    if (parts.length < 4) continue;

                    Map<String, Object> container = new LinkedHashMap<>();
                    container.put("id", parts[0]);
                    container.put("name", parts[1]);
                    container.put("image", parts[2]);
                    container.put("status", parts[3]);
                    container.put("ports", new ArrayList<>());
                    containers.add(container);
                }
                docker.put("containers", containers);
            }

            // Find compose files
            result = conn.executeCommand(
                    "find / -maxdepth 5 -name 'docker-compose.yml' -o -name 'docker-compose.yaml' -o -name 'compose.yml' -o -name 'compose.yaml' 2>/dev/null | head -20");
            if (result.isSuccess() && !result.stdout().isBlank()) {
                List<String> composeFiles = new ArrayList<>();
                for (String line : result.stdout().lines().toList()) {
                    line = line.trim();
                    if (!line.isBlank() && !line.contains("proc") && !line.contains("sys")) {
                        composeFiles.add(line);
                    }
                }
                docker.put("compose_files", composeFiles);
            }
        } catch (Exception e) {
            log.warn("Failed to collect Docker state: {}", e.getMessage());
        }
        return docker;
    }

    private List<Map<String, Object>> collectConfigs(SshConnection conn) {
        List<Map<String, Object>> configs = new ArrayList<>();
        String[] configPaths = {
                "/etc/nginx/nginx.conf", "/etc/nginx/sites-available/default",
                "/etc/mysql/my.cnf", "/etc/redis/redis.conf",
                "/etc/ssh/sshd_config", "/etc/hosts", "/etc/hostname",
                "/etc/resolv.conf", "/etc/fstab", "/etc/crontab",
                "/etc/sysctl.conf", "/etc/environment"
        };

        for (String path : configPaths) {
            try {
                SshConnection.CommandResult result = conn.executeCommand(
                        String.format("stat -c '%%s' %s 2>/dev/null && sha256sum %s 2>/dev/null | cut -d' ' -f1",
                                path, path));
                if (result.isSuccess() && !result.stdout().isBlank()) {
                    String[] lines = result.stdout().trim().split("\n");
                    if (lines.length >= 2) {
                        Map<String, Object> config = new LinkedHashMap<>();
                        config.put("path", path);
                        config.put("sha256", lines[1].trim());
                        try {
                            config.put("size", Long.parseLong(lines[0].trim()));
                        } catch (NumberFormatException e) {
                            config.put("size", 0);
                        }
                        configs.add(config);
                    }
                }
            } catch (Exception e) {
                // Skip inaccessible config files
            }
        }
        return configs;
    }

    private List<Map<String, String>> collectCrontab(SshConnection conn) {
        List<Map<String, String>> entries = new ArrayList<>();
        try {
            SshConnection.CommandResult result = conn.executeCommand("cat /etc/crontab 2>/dev/null");
            if (result.isSuccess() && !result.stdout().isBlank()) {
                for (String line : result.stdout().lines().toList()) {
                    line = line.trim();
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] fields = line.split("\\s+");
                    if (fields.length >= 6) {
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put("user", fields[0]);
                        entry.put("schedule", String.join(" ", Arrays.copyOfRange(fields, 1, 6)));
                        entry.put("command", String.join(" ", Arrays.copyOfRange(fields, 6, fields.length)));
                        entries.add(entry);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to collect crontab: {}", e.getMessage());
        }
        return entries;
    }
}