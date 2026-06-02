package com.chronovault.service;

import com.chronovault.dto.drift.*;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriftDetectionService {

    private final ServerRepository serverRepository;
    private final SnapshotRepository snapshotRepository;
    private final SshConnectionManager sshManager;

    private static final List<String> MONITORED_CONFIG_FILES = List.of(
            "/etc/nginx/nginx.conf",
            "/etc/ssh/sshd_config",
            "/etc/crontab",
            "/etc/hosts",
            "/etc/resolv.conf"
    );

    /**
     * Run full drift detection for a server.
     */
    public DriftReportDTO detectDrift(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        SshConnection conn;
        try {
            conn = sshManager.getConnection(server);
        } catch (Exception e) {
            throw new BadRequestException("无法连接到服务器: " + e.getMessage());
        }

        List<ContainerDrift> containerDrifts = detectContainerDrift(conn, serverId);
        List<FileDrift> fileDrifts = detectFileDrift(conn);
        List<PortDrift> portDrifts = detectPortDrift(conn);

        int totalChanges = containerDrifts.size() + fileDrifts.size() + portDrifts.size();
        String status = totalChanges == 0 ? "CLEAN" : totalChanges < 3 ? "MINOR" : "CHANGED";

        return new DriftReportDTO(
                serverId,
                server.getName(),
                totalChanges,
                containerDrifts,
                fileDrifts,
                portDrifts,
                status,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    private List<ContainerDrift> detectContainerDrift(SshConnection conn, Long serverId) {
        List<ContainerDrift> drifts = new ArrayList<>();
        try {
            // Get current running containers
            SshConnection.CommandResult psResult = conn.executeCommand(
                    "docker ps --format '{{.Names}}|{{.Image}}|{{.Status}}' 2>/dev/null");
            if (!psResult.isSuccess() || psResult.stdout().isBlank()) {
                return drifts;
            }

            Set<String> currentContainers = new HashSet<>();
            for (String line : psResult.stdout().lines().toList()) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|", 3);
                if (parts.length >= 1) {
                    currentContainers.add(parts[0].trim());
                }
            }

            // Compare with snapshot manifest if available
            // For now, detect if any containers are in unhealthy state
            SshConnection.CommandResult healthResult = conn.executeCommand(
                    "docker ps --filter 'health=unhealthy' --format '{{.Names}}' 2>/dev/null");
            if (healthResult.isSuccess() && !healthResult.stdout().isBlank()) {
                for (String name : healthResult.stdout().lines().toList()) {
                    if (!name.isBlank()) {
                        drifts.add(new ContainerDrift(name.trim(), "unhealthy",
                                "HEALTH_CHANGED", "容器处于不健康状态"));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Container drift detection failed: {}", e.getMessage());
        }
        return drifts;
    }

    private List<FileDrift> detectFileDrift(SshConnection conn) {
        List<FileDrift> drifts = new ArrayList<>();
        for (String filePath : MONITORED_CONFIG_FILES) {
            try {
                SshConnection.CommandResult checkResult = conn.executeCommand(
                        "test -f " + filePath + " && md5sum " + filePath + " 2>/dev/null | awk '{print $1}'");
                if (checkResult.isSuccess() && !checkResult.stdout().isBlank()) {
                    String currentHash = checkResult.stdout().trim();
                    // In a real implementation, compare against stored baseline
                    // For now, just report the file exists with its hash
                    drifts.add(new FileDrift(filePath, "CHECKED", currentHash, null,
                            "文件已检查"));
                }
            } catch (Exception e) {
                log.debug("File check failed for {}: {}", filePath, e.getMessage());
            }
        }
        return drifts;
    }

    private List<PortDrift> detectPortDrift(SshConnection conn) {
        List<PortDrift> drifts = new ArrayList<>();
        try {
            SshConnection.CommandResult portResult = conn.executeCommand(
                    "ss -tlnp 2>/dev/null | tail -n +2 | awk '{print $4, $6}'");
            if (portResult.isSuccess() && !portResult.stdout().isBlank()) {
                for (String line : portResult.stdout().lines().toList()) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length >= 1) {
                        String addr = parts[0];
                        String portStr = addr.contains(":") ? addr.substring(addr.lastIndexOf(':') + 1) : "0";
                        try {
                            int port = Integer.parseInt(portStr);
                            if (port > 0 && port < 65536) {
                                drifts.add(new PortDrift(port, "tcp", "LISTENING",
                                        "端口正在监听"));
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Port drift detection failed: {}", e.getMessage());
        }
        return drifts;
    }
}