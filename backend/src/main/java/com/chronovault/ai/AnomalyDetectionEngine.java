package com.chronovault.ai;

import com.chronovault.cache.CacheService;
import com.chronovault.cache.CacheKeyBuilder;
import com.chronovault.dto.ai.AnomalyDetectionDTO;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.Server;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.ServerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Anomaly Detection Engine: compares current server state (from latest state.json)
 * against historical baseline (from past N snapshots) to automatically flag anomalies.
 *
 * Detects: unexpected ports, new/removed services, package changes,
 * config file modifications, Docker container changes, crontab changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionEngine {

    private final SnapshotRepository snapshotRepository;
    private final ServerRepository serverRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    // Baseline window: how many past snapshots to consider "normal"
    private static final int BASELINE_SNAPSHOT_COUNT = 10;
    // Minimum snapshots required to build a meaningful baseline
    private static final int MIN_BASELINE_SNAPSHOTS = 2;

    /**
     * Detect anomalies for a specific server by comparing its latest state.json
     * against the historical baseline built from previous snapshots.
     */
    public AnomalyDetectionDTO detectAnomalies(Long serverId) {
        log.info("[ANOMALY_DETECT] Starting anomaly detection for server {}", serverId);

        Server server = serverRepository.findById(serverId).orElse(null);
        String serverName = server != null ? server.getName() : "Unknown";

        List<Snapshot> snapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId);

        if (snapshots.isEmpty()) {
            return new AnomalyDetectionDTO(
                    serverId, serverName, List.of(), "无快照数据，无法进行异常检测", LocalDateTime.now()
            );
        }

        // Get the latest snapshot (current state)
        Snapshot latest = snapshots.get(0);
        String currentStateJson = latest.getStateJson();

        if (currentStateJson == null || currentStateJson.isBlank()) {
            return new AnomalyDetectionDTO(
                    serverId, serverName, List.of(),
                    "最新快照缺少 state.json 数据，无法进行异常检测", LocalDateTime.now()
            );
        }

        // Build baseline from historical snapshots (excluding the latest)
        List<Snapshot> historicalSnapshots = snapshots.stream()
                .skip(1)
                .limit(BASELINE_SNAPSHOT_COUNT)
                .filter(s -> s.getStateJson() != null && !s.getStateJson().isBlank())
                .toList();

        if (historicalSnapshots.size() < MIN_BASELINE_SNAPSHOTS) {
            // Not enough historical data — only compare with previous snapshot if available
            if (!historicalSnapshots.isEmpty()) {
                return detectWithPrevious(currentStateJson, historicalSnapshots.get(0), serverId, serverName);
            }
            return new AnomalyDetectionDTO(
                    serverId, serverName, List.of(),
                    "历史数据不足（需要至少 " + MIN_BASELINE_SNAPSHOTS + " 个含 state.json 的快照），无法建立基线", LocalDateTime.now()
            );
        }

        // Build baseline from historical snapshots
        Baseline baseline = buildBaseline(historicalSnapshots);

        // Compare current state against baseline
        List<AnomalyDetectionDTO.Anomaly> anomalies = new ArrayList<>();
        anomalies.addAll(detectPortAnomalies(currentStateJson, baseline, serverId));
        anomalies.addAll(detectServiceAnomalies(currentStateJson, baseline, serverId));
        anomalies.addAll(detectPackageAnomalies(currentStateJson, baseline, serverId));
        anomalies.addAll(detectConfigAnomalies(currentStateJson, baseline, serverId));
        anomalies.addAll(detectDockerAnomalies(currentStateJson, baseline, serverId));
        anomalies.addAll(detectCrontabAnomalies(currentStateJson, baseline, serverId));
        anomalies.addAll(detectOsAnomalies(currentStateJson, baseline, serverId));

        // Sort by severity: CRITICAL first
        anomalies.sort(Comparator.comparingInt(a -> switch (a.severity()) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        }));

        int criticalCount = (int) anomalies.stream().filter(a -> "CRITICAL".equals(a.severity())).count();
        int warningCount = (int) anomalies.stream().filter(a -> "WARNING".equals(a.severity())).count();

        String summary;
        if (anomalies.isEmpty()) {
            summary = "未检测到异常，系统状态与历史基线一致";
        } else {
            summary = String.format("检测到 %d 个异常（严重: %d，警告: %d），建议检查标记为 CRITICAL 的项目",
                    anomalies.size(), criticalCount, warningCount);
        }

        log.info("[ANOMALY_DETECT] Server {}: {} anomalies (CRITICAL={}, WARNING={})",
                serverId, anomalies.size(), criticalCount, warningCount);

        return new AnomalyDetectionDTO(serverId, serverName, anomalies, summary, LocalDateTime.now());
    }

    /**
     * Detect anomalies for all servers at once.
     */
    public List<AnomalyDetectionDTO> detectAllAnomalies() {
        List<Server> servers = serverRepository.findAll();
        List<AnomalyDetectionDTO> results = new ArrayList<>();
        for (Server server : servers) {
            try {
                results.add(detectAnomalies(server.getId()));
            } catch (Exception e) {
                log.error("[ANOMALY_DETECT] Failed to detect anomalies for server {}: {}",
                        server.getId(), e.getMessage());
            }
        }
        return results;
    }

    // --- Baseline building ---

    private record Baseline(
            Set<Integer> knownPorts,
            Set<String> knownPortProcesses,
            Map<String, String> knownServices,  // name -> status
            Map<String, String> knownPackages,   // name -> version
            Set<String> knownConfigPaths,
            Map<String, String> knownConfigHashes,  // path -> sha256
            Set<String> knownContainerNames,
            Set<String> knownCrontabCommands,
            String knownOsName,
            String knownKernel
    ) {}

    private Baseline buildBaseline(List<Snapshot> historicalSnapshots) {
        Set<Integer> ports = new HashSet<>();
        Set<String> portProcesses = new HashSet<>();
        Map<String, String> services = new LinkedHashMap<>();
        Map<String, String> packages = new LinkedHashMap<>();
        Set<String> configPaths = new HashSet<>();
        Map<String, String> configHashes = new LinkedHashMap<>();
        Set<String> containerNames = new HashSet<>();
        Set<String> crontabCommands = new HashSet<>();
        String osName = null;
        String kernel = null;

        for (Snapshot snap : historicalSnapshots) {
            try {
                JsonNode root = objectMapper.readTree(snap.getStateJson());

                // Ports
                JsonNode portsNode = root.path("ports");
                if (portsNode.isArray()) {
                    for (JsonNode port : portsNode) {
                        int portNum = port.path("port").asInt(0);
                        if (portNum > 0) ports.add(portNum);
                        String process = port.path("process").asText("");
                        if (!process.isEmpty()) portProcesses.add(process);
                    }
                }

                // Services — keep the most common status for each service
                JsonNode servicesNode = root.path("services");
                if (servicesNode.isArray()) {
                    for (JsonNode svc : servicesNode) {
                        String name = svc.path("name").asText("");
                        String status = svc.path("status").asText("");
                        if (!name.isEmpty()) {
                            // If already seen, keep the status that appears most (majority vote)
                            services.merge(name, status, (old, cur) -> {
                                // Simple: just keep the latest value (from the closest snapshot)
                                return cur;
                            });
                        }
                    }
                }

                // Packages
                JsonNode packagesNode = root.path("packages");
                if (packagesNode.isArray()) {
                    for (JsonNode pkg : packagesNode) {
                        String name = pkg.path("name").asText("");
                        String version = pkg.path("version").asText("");
                        if (!name.isEmpty() && !version.isEmpty()) {
                            packages.put(name, version);
                        }
                    }
                }

                // Configs
                JsonNode configsNode = root.path("configs");
                if (configsNode.isArray()) {
                    for (JsonNode cfg : configsNode) {
                        String path = cfg.path("path").asText("");
                        String hash = cfg.path("sha256").asText("");
                        if (!path.isEmpty()) {
                            configPaths.add(path);
                            if (!hash.isEmpty()) configHashes.put(path, hash);
                        }
                    }
                }

                // Docker containers
                JsonNode dockerNode = root.path("docker").path("containers");
                if (dockerNode.isArray()) {
                    for (JsonNode container : dockerNode) {
                        String name = container.path("name").asText("");
                        if (!name.isEmpty()) containerNames.add(name);
                    }
                }

                // Crontab
                JsonNode crontabNode = root.path("crontab");
                if (crontabNode.isArray()) {
                    for (JsonNode cron : crontabNode) {
                        String cmd = cron.path("command").asText("");
                        if (!cmd.isEmpty()) crontabCommands.add(cmd);
                    }
                }

                // OS
                JsonNode osNode = root.path("os");
                if (osNode.isObject()) {
                    if (osName == null) osName = osNode.path("name").asText("");
                    if (kernel == null) kernel = osNode.path("kernel").asText("");
                }
            } catch (Exception e) {
                log.debug("Failed to parse state.json for baseline: {}", e.getMessage());
            }
        }

        return new Baseline(ports, portProcesses, services, packages, configPaths,
                configHashes, containerNames, crontabCommands, osName, kernel);
    }

    // --- Anomaly detection methods ---

    private List<AnomalyDetectionDTO.Anomaly> detectPortAnomalies(
            String currentJson, Baseline baseline, Long serverId) {
        List<AnomalyDetectionDTO.Anomaly> anomalies = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(currentJson);
            JsonNode portsNode = root.path("ports");
            if (!portsNode.isArray()) return anomalies;

            Set<Integer> currentPorts = new HashSet<>();
            for (JsonNode port : portsNode) {
                int portNum = port.path("port").asInt(0);
                if (portNum <= 0) continue;
                currentPorts.add(portNum);

                if (!baseline.knownPorts().contains(portNum)) {
                    String process = port.path("process").asText("unknown");
                    String protocol = port.path("protocol").asText("tcp");
                    // Well-known safe ports (SSH, HTTP, HTTPS) are WARNING; others are CRITICAL
                    String severity = isWellKnownPort(portNum) ? "WARNING" : "CRITICAL";
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "PORT", severity,
                            String.format("发现新的监听端口: %d/%s (进程: %s)", portNum, protocol, process),
                            String.format("端口 %d 在历史基线中从未出现，可能为新服务或异常监听", portNum),
                            serverId, Map.of("port", portNum, "protocol", protocol, "process", process)
                    ));
                }
            }

            // Check for ports that disappeared
            for (Integer knownPort : baseline.knownPorts()) {
                if (!currentPorts.contains(knownPort)) {
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "PORT", "INFO",
                            String.format("端口 %d 已不再监听", knownPort),
                            "该端口在历史基线中存在但当前已关闭，可能是服务停止或配置变更",
                            serverId, Map.of("port", knownPort)
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Port anomaly detection failed: {}", e.getMessage());
        }
        return anomalies;
    }

    private List<AnomalyDetectionDTO.Anomaly> detectServiceAnomalies(
            String currentJson, Baseline baseline, Long serverId) {
        List<AnomalyDetectionDTO.Anomaly> anomalies = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(currentJson);
            JsonNode servicesNode = root.path("services");
            if (!servicesNode.isArray()) return anomalies;

            Set<String> currentServiceNames = new HashSet<>();
            for (JsonNode svc : servicesNode) {
                String name = svc.path("name").asText("");
                String status = svc.path("status").asText("");
                if (name.isEmpty()) continue;
                currentServiceNames.add(name);

                if (!baseline.knownServices().containsKey(name)) {
                    // New service not seen in baseline
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "SERVICE", "WARNING",
                            String.format("发现新服务: %s (状态: %s)", name, status),
                            "该服务在历史基线中不存在，可能是新安装的服务或异常进程",
                            serverId, Map.of("serviceName", name, "status", status)
                    ));
                } else {
                    // Service exists but status changed (e.g., was active, now inactive)
                    String baselineStatus = baseline.knownServices().get(name);
                    if (!status.equals(baselineStatus) && !status.isEmpty()) {
                        anomalies.add(new AnomalyDetectionDTO.Anomaly(
                                "SERVICE", "INFO",
                                String.format("服务 %s 状态变更: %s → %s", name, baselineStatus, status),
                                "服务状态与历史基线不同，可能是正常维护或异常停止",
                                serverId, Map.of("serviceName", name, "from", baselineStatus, "to", status)
                        ));
                    }
                }
            }

            // Check for services that disappeared
            for (String knownService : baseline.knownServices().keySet()) {
                if (!currentServiceNames.contains(knownService)) {
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "SERVICE", "WARNING",
                            String.format("服务 %s 已消失", knownService),
                            "该服务在历史基线中存在但当前未检测到，可能被卸载或停止",
                            serverId, Map.of("serviceName", knownService)
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Service anomaly detection failed: {}", e.getMessage());
        }
        return anomalies;
    }

    private List<AnomalyDetectionDTO.Anomaly> detectPackageAnomalies(
            String currentJson, Baseline baseline, Long serverId) {
        List<AnomalyDetectionDTO.Anomaly> anomalies = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(currentJson);
            JsonNode packagesNode = root.path("packages");
            if (!packagesNode.isArray()) return anomalies;

            Set<String> currentPackageNames = new HashSet<>();
            for (JsonNode pkg : packagesNode) {
                String name = pkg.path("name").asText("");
                String version = pkg.path("version").asText("");
                if (name.isEmpty() || version.isEmpty()) continue;
                currentPackageNames.add(name);

                if (!baseline.knownPackages().containsKey(name)) {
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "PACKAGE", "INFO",
                            String.format("新安装包: %s %s", name, version),
                            "该软件包在历史基线中不存在",
                            serverId, Map.of("packageName", name, "version", version)
                    ));
                } else {
                    String baselineVersion = baseline.knownPackages().get(name);
                    if (!version.equals(baselineVersion)) {
                        anomalies.add(new AnomalyDetectionDTO.Anomaly(
                                "PACKAGE", "WARNING",
                                String.format("包版本变更: %s %s → %s", name, baselineVersion, version),
                                "软件包版本与历史基线不同，请确认是否为预期的升级/降级",
                                serverId, Map.of("packageName", name, "from", baselineVersion, "to", version)
                        ));
                    }
                }
            }

            // Check for removed packages
            for (Map.Entry<String, String> entry : baseline.knownPackages().entrySet()) {
                if (!currentPackageNames.contains(entry.getKey())) {
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "PACKAGE", "WARNING",
                            String.format("包已移除: %s (版本: %s)", entry.getKey(), entry.getValue()),
                            "该软件包在历史基线中存在但当前已不存在",
                            serverId, Map.of("packageName", entry.getKey(), "version", entry.getValue())
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Package anomaly detection failed: {}", e.getMessage());
        }
        return anomalies;
    }

    private List<AnomalyDetectionDTO.Anomaly> detectConfigAnomalies(
            String currentJson, Baseline baseline, Long serverId) {
        List<AnomalyDetectionDTO.Anomaly> anomalies = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(currentJson);
            JsonNode configsNode = root.path("configs");
            if (!configsNode.isArray()) return anomalies;

            Set<String> currentConfigPaths = new HashSet<>();
            for (JsonNode cfg : configsNode) {
                String path = cfg.path("path").asText("");
                String hash = cfg.path("sha256").asText("");
                if (path.isEmpty()) continue;
                currentConfigPaths.add(path);

                if (baseline.knownConfigHashes().containsKey(path)) {
                    String baselineHash = baseline.knownConfigHashes().get(path);
                    if (!hash.isEmpty() && !hash.equals(baselineHash)) {
                        anomalies.add(new AnomalyDetectionDTO.Anomaly(
                                "CONFIG", "CRITICAL",
                                String.format("配置文件变更: %s", path),
                                String.format("文件 SHA256 从 %s... 变为 %s...，配置内容已修改",
                                        baselineHash.substring(0, Math.min(8, baselineHash.length())),
                                        hash.substring(0, Math.min(8, hash.length()))),
                                serverId, Map.of("path", path, "fromHash", baselineHash, "toHash", hash)
                        ));
                    }
                } else if (baseline.knownConfigPaths().contains(path)) {
                    // Path known but hash not in baseline (baseline might not have had this specific file)
                } else {
                    // Completely new config file
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "CONFIG", "WARNING",
                            String.format("新配置文件: %s", path),
                            "该配置文件在历史基线中不存在",
                            serverId, Map.of("path", path, "hash", hash)
                    ));
                }
            }

            // Check for removed config files
            for (String knownPath : baseline.knownConfigPaths()) {
                if (!currentConfigPaths.contains(knownPath)) {
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "CONFIG", "CRITICAL",
                            String.format("配置文件消失: %s", knownPath),
                            "该配置文件在历史基线中存在但当前已不存在，可能被删除或移动",
                            serverId, Map.of("path", knownPath)
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Config anomaly detection failed: {}", e.getMessage());
        }
        return anomalies;
    }

    private List<AnomalyDetectionDTO.Anomaly> detectDockerAnomalies(
            String currentJson, Baseline baseline, Long serverId) {
        List<AnomalyDetectionDTO.Anomaly> anomalies = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(currentJson);
            JsonNode dockerNode = root.path("docker").path("containers");
            if (!dockerNode.isArray()) return anomalies;

            Set<String> currentContainerNames = new HashSet<>();
            for (JsonNode container : dockerNode) {
                String name = container.path("name").asText("");
                String image = container.path("image").asText("");
                String status = container.path("status").asText("");
                if (name.isEmpty()) continue;
                currentContainerNames.add(name);

                if (!baseline.knownContainerNames().contains(name)) {
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "DOCKER", "WARNING",
                            String.format("新容器: %s (镜像: %s, 状态: %s)", name, image, status),
                            "该 Docker 容器在历史基线中不存在，可能是新部署的容器",
                            serverId, Map.of("containerName", name, "image", image, "status", status)
                    ));
                }
            }

            // Check for removed containers
            for (String knownName : baseline.knownContainerNames()) {
                if (!currentContainerNames.contains(knownName)) {
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "DOCKER", "CRITICAL",
                            String.format("容器消失: %s", knownName),
                            "该 Docker 容器在历史基线中存在但当前已不存在，可能被删除或停止",
                            serverId, Map.of("containerName", knownName)
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Docker anomaly detection failed: {}", e.getMessage());
        }
        return anomalies;
    }

    private List<AnomalyDetectionDTO.Anomaly> detectCrontabAnomalies(
            String currentJson, Baseline baseline, Long serverId) {
        List<AnomalyDetectionDTO.Anomaly> anomalies = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(currentJson);
            JsonNode crontabNode = root.path("crontab");
            if (!crontabNode.isArray()) return anomalies;

            Set<String> currentCommands = new HashSet<>();
            for (JsonNode cron : crontabNode) {
                String cmd = cron.path("command").asText("");
                String schedule = cron.path("schedule").asText("");
                String user = cron.path("user").asText("");
                if (cmd.isEmpty()) continue;
                currentCommands.add(cmd);

                if (!baseline.knownCrontabCommands().contains(cmd)) {
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "CRONTAB", "WARNING",
                            String.format("新定时任务: [%s] %s (用户: %s)", schedule, cmd, user),
                            "该定时任务在历史基线中不存在，可能是新添加的任务",
                            serverId, Map.of("command", cmd, "schedule", schedule, "user", user)
                    ));
                }
            }

            // Check for removed crons
            for (String knownCmd : baseline.knownCrontabCommands()) {
                if (!currentCommands.contains(knownCmd)) {
                    anomalies.add(new AnomalyDetectionDTO.Anomaly(
                            "CRONTAB", "INFO",
                            String.format("定时任务消失: %s", knownCmd),
                            "该定时任务在历史基线中存在但当前已不存在",
                            serverId, Map.of("command", knownCmd)
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Crontab anomaly detection failed: {}", e.getMessage());
        }
        return anomalies;
    }

    private List<AnomalyDetectionDTO.Anomaly> detectOsAnomalies(
            String currentJson, Baseline baseline, Long serverId) {
        List<AnomalyDetectionDTO.Anomaly> anomalies = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(currentJson);
            JsonNode osNode = root.path("os");
            if (!osNode.isObject()) return anomalies;

            String currentKernel = osNode.path("kernel").asText("");
            if (!currentKernel.isEmpty() && baseline.knownKernel() != null
                    && !currentKernel.equals(baseline.knownKernel())) {
                anomalies.add(new AnomalyDetectionDTO.Anomaly(
                        "OS", "CRITICAL",
                        String.format("内核版本变更: %s → %s", baseline.knownKernel(), currentKernel),
                        "操作系统内核版本发生变化，这通常是高风险操作，需要重启生效",
                        serverId, Map.of("from", baseline.knownKernel(), "to", currentKernel)
                ));
            }
        } catch (Exception e) {
            log.debug("OS anomaly detection failed: {}", e.getMessage());
        }
        return anomalies;
    }

    // --- Comparison with single previous snapshot (fallback) ---

    private AnomalyDetectionDTO detectWithPrevious(
            String currentJson, Snapshot previous, Long serverId, String serverName) {
        List<AnomalyDetectionDTO.Anomaly> anomalies = new ArrayList<>();
        try {
            Baseline previousBaseline = buildBaseline(List.of(previous));
            anomalies.addAll(detectPortAnomalies(currentJson, previousBaseline, serverId));
            anomalies.addAll(detectServiceAnomalies(currentJson, previousBaseline, serverId));
            anomalies.addAll(detectPackageAnomalies(currentJson, previousBaseline, serverId));
            anomalies.addAll(detectConfigAnomalies(currentJson, previousBaseline, serverId));
            anomalies.addAll(detectDockerAnomalies(currentJson, previousBaseline, serverId));
            anomalies.addAll(detectCrontabAnomalies(currentJson, previousBaseline, serverId));
            anomalies.addAll(detectOsAnomalies(currentJson, previousBaseline, serverId));

            anomalies.sort(Comparator.comparingInt(a -> switch (a.severity()) {
                case "CRITICAL" -> 0;
                case "WARNING" -> 1;
                default -> 2;
            }));
        } catch (Exception e) {
            log.debug("Previous snapshot comparison failed: {}", e.getMessage());
        }

        String summary = anomalies.isEmpty()
                ? "与上一次快照相比未检测到异常"
                : String.format("与上一次快照相比检测到 %d 个差异", anomalies.size());

        return new AnomalyDetectionDTO(serverId, serverName, anomalies, summary, LocalDateTime.now());
    }

    private boolean isWellKnownPort(int port) {
        return port == 22 || port == 80 || port == 443 || port == 8080 || port == 8443;
    }
}
