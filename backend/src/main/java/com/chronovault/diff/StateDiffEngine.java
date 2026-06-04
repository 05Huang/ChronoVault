package com.chronovault.diff;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Compares two state.json snapshots and produces a structured diff.
 * This is the core differentiator — comparing system state between snapshots,
 * not just file-level changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StateDiffEngine {

    private final ObjectMapper objectMapper;

    /**
     * Compare two state.json strings and produce a structured diff result.
     * Returns a result with an error field if the diff computation fails.
     */
    public StateDiffResult diff(String stateJsonA, String stateJsonB) {
        if (stateJsonA == null || stateJsonB == null) {
            return StateDiffResult.empty();
        }

        try {
            JsonNode nodeA = objectMapper.readTree(stateJsonA);
            JsonNode nodeB = objectMapper.readTree(stateJsonB);

            // Compare packages
            PackageDiff pkgDiff = diffPackages(
                    nodeA.path("packages"),
                    nodeB.path("packages"));

            // Compare services
            ServiceDiff svcDiff = diffServices(
                    nodeA.path("services"),
                    nodeB.path("services"));

            // Compare ports
            PortDiff portDiff = diffPorts(
                    nodeA.path("ports"),
                    nodeB.path("ports"));

            // Compare docker containers
            DockerDiff dockerDiff = diffDocker(
                    nodeA.path("docker"),
                    nodeB.path("docker"));

            // Compare config files
            ConfigDiff configDiff = diffConfigs(
                    nodeA.path("configs"),
                    nodeB.path("configs"));

            // Compare crontab
            CrontabDiff crontabDiff = diffCrontab(
                    nodeA.path("crontab"),
                    nodeB.path("crontab"));

            // Build result
            StateDiffResult result = new StateDiffResult(
                    pkgDiff, svcDiff, portDiff, dockerDiff, configDiff, crontabDiff,
                    generateSummary(pkgDiff, svcDiff, portDiff, dockerDiff, configDiff, crontabDiff),
                    null); // null error = success

            return result;
        } catch (Exception e) {
            log.warn("[DIFF_FAILED] Failed to diff state snapshots: {}", e.getMessage(), e);
            // Return result with error info instead of silently empty result
            return StateDiffResult.error("Diff computation failed: " + e.getMessage());
        }
    }

    private PackageDiff diffPackages(JsonNode packagesA, JsonNode packagesB) {
        PackageDiff diff = new PackageDiff();
        if (packagesA.isMissingNode() || packagesB.isMissingNode()) return diff;

        Map<String, String> mapA = buildPackageMap(packagesA);
        Map<String, String> mapB = buildPackageMap(packagesB);

        // Added: in B but not in A
        for (Map.Entry<String, String> entry : mapB.entrySet()) {
            if (!mapA.containsKey(entry.getKey())) {
                diff.added.add(new PackageInfo(entry.getKey(), entry.getValue()));
            }
        }

        // Removed: in A but not in B
        for (Map.Entry<String, String> entry : mapA.entrySet()) {
            if (!mapB.containsKey(entry.getKey())) {
                diff.removed.add(new PackageInfo(entry.getKey(), entry.getValue()));
            }
        }

        // Upgraded: in both but different version
        for (Map.Entry<String, String> entry : mapA.entrySet()) {
            String versionB = mapB.get(entry.getKey());
            if (versionB != null && !entry.getValue().equals(versionB)) {
                diff.upgraded.add(new PackageUpgrade(entry.getKey(), entry.getValue(), versionB));
            }
        }

        return diff;
    }

    private ServiceDiff diffServices(JsonNode servicesA, JsonNode servicesB) {
        ServiceDiff diff = new ServiceDiff();
        if (servicesA.isMissingNode() || servicesB.isMissingNode()) return diff;

        Map<String, JsonNode> mapA = buildServiceMap(servicesA);
        Map<String, JsonNode> mapB = buildServiceMap(servicesB);

        for (Map.Entry<String, JsonNode> entry : mapB.entrySet()) {
            JsonNode prev = mapA.get(entry.getKey());
            if (prev == null) {
                diff.added.add(entry.getKey());
            } else {
                String statusA = prev.path("status").asText("");
                String statusB = entry.getValue().path("status").asText("");
                boolean enabledA = prev.path("enabled").asBoolean(false);
                boolean enabledB = entry.getValue().path("enabled").asBoolean(false);

                if (!statusA.equals(statusB) || enabledA != enabledB) {
                    ServiceChange change = new ServiceChange(entry.getKey());
                    change.fromStatus = statusA;
                    change.toStatus = statusB;
                    change.fromEnabled = enabledA;
                    change.toEnabled = enabledB;
                    diff.changed.add(change);
                }
            }
        }

        for (Map.Entry<String, JsonNode> entry : mapA.entrySet()) {
            if (!mapB.containsKey(entry.getKey())) {
                diff.removed.add(entry.getKey());
            }
        }

        return diff;
    }

    private PortDiff diffPorts(JsonNode portsA, JsonNode portsB) {
        PortDiff diff = new PortDiff();
        if (portsA.isMissingNode() || portsB.isMissingNode()) return diff;

        Set<String> setA = buildPortSet(portsA);
        Set<String> setB = buildPortSet(portsB);

        for (String port : setB) {
            if (!setA.contains(port)) {
                diff.added.add(port);
            }
        }
        for (String port : setA) {
            if (!setB.contains(port)) {
                diff.removed.add(port);
            }
        }

        return diff;
    }

    private DockerDiff diffDocker(JsonNode dockerA, JsonNode dockerB) {
        DockerDiff diff = new DockerDiff();
        if (dockerA.isMissingNode() || dockerB.isMissingNode()) return diff;

        Map<String, JsonNode> containersA = buildContainerMap(dockerA.path("containers"));
        Map<String, JsonNode> containersB = buildContainerMap(dockerB.path("containers"));

        for (Map.Entry<String, JsonNode> entry : containersB.entrySet()) {
            if (!containersA.containsKey(entry.getKey())) {
                diff.containersAdded.add(entry.getKey());
            } else {
                String statusA = containersA.get(entry.getKey()).path("status").asText("");
                String statusB = entry.getValue().path("status").asText("");
                if (!statusA.equals(statusB)) {
                    diff.containersChanged.add(entry.getKey());
                }
            }
        }
        for (Map.Entry<String, JsonNode> entry : containersA.entrySet()) {
            if (!containersB.containsKey(entry.getKey())) {
                diff.containersRemoved.add(entry.getKey());
            }
        }

        return diff;
    }

    private ConfigDiff diffConfigs(JsonNode configsA, JsonNode configsB) {
        ConfigDiff diff = new ConfigDiff();
        if (configsA.isMissingNode() || configsB.isMissingNode()) return diff;

        Map<String, String> hashA = buildConfigHashMap(configsA);
        Map<String, String> hashB = buildConfigHashMap(configsB);

        for (Map.Entry<String, String> entry : hashB.entrySet()) {
            if (!hashA.containsKey(entry.getKey())) {
                diff.added.add(entry.getKey());
            } else if (!hashA.get(entry.getKey()).equals(entry.getValue())) {
                diff.changed.add(entry.getKey());
            }
        }
        for (Map.Entry<String, String> entry : hashA.entrySet()) {
            if (!hashB.containsKey(entry.getKey())) {
                diff.removed.add(entry.getKey());
            }
        }

        return diff;
    }

    private CrontabDiff diffCrontab(JsonNode crontabA, JsonNode crontabB) {
        CrontabDiff diff = new CrontabDiff();
        if (crontabA.isMissingNode() || crontabB.isMissingNode()) return diff;

        Set<String> entriesA = buildCrontabSet(crontabA);
        Set<String> entriesB = buildCrontabSet(crontabB);

        for (String entry : entriesB) {
            if (!entriesA.contains(entry)) {
                diff.added.add(entry);
            }
        }
        for (String entry : entriesA) {
            if (!entriesB.contains(entry)) {
                diff.removed.add(entry);
            }
        }

        return diff;
    }

    // ===== Helper methods =====

    private Map<String, String> buildPackageMap(JsonNode packages) {
        Map<String, String> map = new LinkedHashMap<>();
        if (packages.isArray()) {
            for (JsonNode pkg : packages) {
                String name = pkg.path("name").asText("");
                String version = pkg.path("version").asText("");
                if (!name.isEmpty()) {
                    map.put(name, version);
                }
            }
        }
        return map;
    }

    private Map<String, JsonNode> buildServiceMap(JsonNode services) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        if (services.isArray()) {
            for (JsonNode svc : services) {
                String name = svc.path("name").asText("");
                if (!name.isEmpty()) {
                    map.put(name, svc);
                }
            }
        }
        return map;
    }

    private Set<String> buildPortSet(JsonNode ports) {
        Set<String> set = new TreeSet<>();
        if (ports.isArray()) {
            for (JsonNode port : ports) {
                int portNum = port.path("port").asInt(0);
                String protocol = port.path("protocol").asText("tcp");
                if (portNum > 0) {
                    set.add(portNum + "/" + protocol);
                }
            }
        }
        return set;
    }

    private Map<String, JsonNode> buildContainerMap(JsonNode containers) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        if (containers.isArray()) {
            for (JsonNode c : containers) {
                String name = c.path("name").asText("");
                if (!name.isEmpty()) {
                    map.put(name, c);
                }
            }
        }
        return map;
    }

    private Map<String, String> buildConfigHashMap(JsonNode configs) {
        Map<String, String> map = new LinkedHashMap<>();
        if (configs.isArray()) {
            for (JsonNode config : configs) {
                String path = config.path("path").asText("");
                String sha256 = config.path("sha256").asText("");
                if (!path.isEmpty()) {
                    map.put(path, sha256);
                }
            }
        }
        return map;
    }

    private Set<String> buildCrontabSet(JsonNode crontab) {
        Set<String> set = new TreeSet<>();
        if (crontab.isArray()) {
            for (JsonNode entry : crontab) {
                String user = entry.path("user").asText("");
                String schedule = entry.path("schedule").asText("");
                String command = entry.path("command").asText("");
                set.add(user + " " + schedule + " " + command);
            }
        }
        return set;
    }

    private DiffSummary generateSummary(PackageDiff pkg, ServiceDiff svc, PortDiff port,
                                         DockerDiff docker, ConfigDiff config, CrontabDiff crontab) {
        DiffSummary summary = new DiffSummary();
        if (pkg != null) {
            summary.packagesAdded = pkg.added.size();
            summary.packagesRemoved = pkg.removed.size();
            summary.packagesUpgraded = pkg.upgraded.size();
        }
        if (svc != null) {
            summary.servicesChanged = svc.changed.size()
                    + svc.added.size() + svc.removed.size();
        }
        if (port != null) {
            summary.portsChanged = port.added.size() + port.removed.size();
        }
        if (docker != null) {
            summary.dockerChanged = docker.containersAdded.size()
                    + docker.containersRemoved.size() + docker.containersChanged.size();
        }
        if (config != null) {
            summary.configsChanged = config.added.size()
                    + config.removed.size() + config.changed.size();
        }
        if (crontab != null) {
            summary.crontabChanged = crontab.added.size() + crontab.removed.size();
        }
        return summary;
    }

    // ===== Result DTOs =====

    public record StateDiffResult(
            PackageDiff packages,
            ServiceDiff services,
            PortDiff ports,
            DockerDiff docker,
            ConfigDiff configs,
            CrontabDiff crontab,
            DiffSummary summary,
            String error
    ) {
        public static StateDiffResult empty() {
            return new StateDiffResult(
                    new PackageDiff(), new ServiceDiff(), new PortDiff(),
                    new DockerDiff(), new ConfigDiff(), new CrontabDiff(),
                    new DiffSummary(), null);
        }

        public static StateDiffResult error(String errorMessage) {
            return new StateDiffResult(
                    new PackageDiff(), new ServiceDiff(), new PortDiff(),
                    new DockerDiff(), new ConfigDiff(), new CrontabDiff(),
                    new DiffSummary(), errorMessage);
        }

        public boolean hasError() {
            return error != null && !error.isBlank();
        }
    }

    public record PackageInfo(String name, String version) {}
    public record PackageUpgrade(String name, String fromVersion, String toVersion) {}

    public static class PackageDiff {
        public List<PackageInfo> added = new ArrayList<>();
        public List<PackageInfo> removed = new ArrayList<>();
        public List<PackageUpgrade> upgraded = new ArrayList<>();
    }

    public static class ServiceDiff {
        public List<String> added = new ArrayList<>();
        public List<String> removed = new ArrayList<>();
        public List<ServiceChange> changed = new ArrayList<>();
    }

    public static class ServiceChange {
        public String name;
        public String fromStatus;
        public String toStatus;
        public boolean fromEnabled;
        public boolean toEnabled;
        public ServiceChange(String name) { this.name = name; }
    }

    public static class PortDiff {
        public List<String> added = new ArrayList<>();
        public List<String> removed = new ArrayList<>();
    }

    public static class DockerDiff {
        public List<String> containersAdded = new ArrayList<>();
        public List<String> containersRemoved = new ArrayList<>();
        public List<String> containersChanged = new ArrayList<>();
    }

    public static class ConfigDiff {
        public List<String> added = new ArrayList<>();
        public List<String> removed = new ArrayList<>();
        public List<String> changed = new ArrayList<>();
    }

    public static class CrontabDiff {
        public List<String> added = new ArrayList<>();
        public List<String> removed = new ArrayList<>();
    }

    public static class DiffSummary {
        public int packagesAdded;
        public int packagesRemoved;
        public int packagesUpgraded;
        public int servicesChanged;
        public int portsChanged;
        public int dockerChanged;
        public int configsChanged;
        public int crontabChanged;
    }
}
