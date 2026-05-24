package com.chronovault.service;

import com.chronovault.entity.Alert;
import com.chronovault.entity.Event;
import com.chronovault.entity.Server;
import com.chronovault.repository.AlertRepository;
import com.chronovault.repository.EventRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.websocket.EventWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerHealthMonitor {

    private final ServerRepository serverRepository;
    private final SshConnectionManager sshManager;
    private final EventWebSocketHandler eventHandler;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AlertRepository alertRepository;
    private final EventRepository eventRepository;

    private static final String HEALTH_CACHE_KEY = "server:health:";
    private static final long CACHE_TTL_MINUTES = 5;

    // Thresholds for alert generation
    private static final double CPU_CRITICAL = 90.0;
    private static final double MEMORY_CRITICAL = 95.0;
    private static final double DISK_CRITICAL = 90.0;
    private static final double DISK_WARNING = 85.0;

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void monitorAllServers() {
        List<Server> servers = serverRepository.findAll();
        if (servers.isEmpty()) return;

        log.info("Starting health check for {} servers", servers.size());
        for (Server server : servers) {
            try {
                checkServerHealth(server);
            } catch (Exception e) {
                log.warn("Health check failed for {}: {}", server.getIp(), e.getMessage());
                markServerOffline(server);
            }
        }
    }

    public Map<String, Object> getServerHealth(Long serverId) {
        String cacheKey = HEALTH_CACHE_KEY + serverId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }
        // If not cached, trigger a fresh check
        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) return Map.of("error", "服务器不存在");
        try {
            return checkServerHealth(server);
        } catch (Exception e) {
            return Map.of("error", e.getMessage(), "status", "UNREACHABLE");
        }
    }

    public Map<String, Object> forceRefresh(Long serverId) {
        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) return Map.of("error", "服务器不存在");
        try {
            return checkServerHealth(server);
        } catch (Exception e) {
            return Map.of("error", e.getMessage(), "status", "UNREACHABLE");
        }
    }

    public Map<String, Object> debugSshOutput(Long serverId) {
        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) return Map.of("error", "服务器不存在");
        try {
            SshConnection conn = sshManager.getConnection(server);
            SshConnection.CommandResult r1 = conn.executeCommand("echo '===SECS===' && cat /proc/uptime 2>/dev/null | awk '{print int($1)}' && echo '===END==='");
            SshConnection.CommandResult r2 = conn.executeCommand("cat /proc/uptime 2>/dev/null");
            SshConnection.CommandResult r3 = conn.executeCommand("uptime");
            SshConnection.CommandResult r4 = conn.executeCommand("echo hello");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chain_exit", r1.exitCode());
            result.put("chain_stdout", r1.stdout());
            result.put("chain_stderr", r1.stderr());
            result.put("cat_exit", r2.exitCode());
            result.put("cat_stdout", r2.stdout());
            result.put("uptime_exit", r3.exitCode());
            result.put("uptime_stdout", r3.stdout());
            result.put("echo_exit", r4.exitCode());
            result.put("echo_stdout", r4.stdout());
            return result;
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            return err;
        }
    }

    private Map<String, Object> checkServerHealth(Server server) throws Exception {
        SshConnection conn = sshManager.getConnection(server);

        // Gather metrics in one SSH session — use simple commands to avoid quoting issues
        String healthScript =
                "echo '===CPU===' && " +
                "top -bn1 | grep 'Cpu(s)' | awk '{print $2}' && " +
                "echo '===MEMORY===' && " +
                "free -m | awk 'NR==2{printf \"%s %s %.1f\", $2, $3, $3*100/$2}' && " +
                "echo '===DISK===' && " +
                "df -h / | awk 'NR==2{print $2, $3, $4, $5}' && " +
                "echo '===UPTIME===' && " +
                "(uptime -p 2>/dev/null || uptime) && " +
                "echo '===LOAD===' && " +
                "cat /proc/loadavg 2>/dev/null && " +
                "echo '===OS===' && " +
                "uname -srm && " +
                "echo '===SECS===' && " +
                "cat /proc/uptime 2>/dev/null | awk '{print int($1)}' && " +
                "echo '===END==='";

        SshConnection.CommandResult result = conn.executeCommand(healthScript);
        log.debug("Health SSH for {} (exit={}): [{}]", server.getIp(), result.exitCode(),
                result.stdout().replace("\n", "\\n").substring(0, Math.min(300, result.stdout().length())));
        if (!result.isSuccess()) {
            throw new RuntimeException("健康检查命令执行失败: " + result.stderr());
        }

        Map<String, Object> health = parseHealthOutput(result.stdout());
        log.debug("Health parsed for {}({}): keys={}", server.getName(), server.getIp(), health.keySet());
        health.put("serverId", server.getId());
        health.put("serverName", server.getName());
        health.put("ip", server.getIp());
        health.put("checkedAt", LocalDateTime.now().toString());
        health.put("status", "ONLINE");

        // Cache in Redis
        String cacheKey = HEALTH_CACHE_KEY + server.getId();
        redisTemplate.opsForValue().set(cacheKey, health, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        // Update server status and persist uptime/OS
        server.setStatus(Server.ServerStatus.RUNNING);
        if (health.get("uptimeSeconds") instanceof Number secs) {
            server.setUptimeSeconds(secs.longValue());
        }
        if (health.get("os") instanceof String os && !os.isBlank()) {
            server.setOs(os.length() > 100 ? os.substring(0, 100) : os);
        }
        serverRepository.save(server);

        // Check thresholds and generate alerts
        checkThresholdsAndAlert(server, health);

        return health;
    }

    /**
     * Parse health check SSH output. Uses regex to extract key-value sections.
     * Input format: ===KEY===\nvalue\n===KEY2===\nvalue2\n...
     */
    private Map<String, Object> parseHealthOutput(String output) {
        Map<String, Object> health = new LinkedHashMap<>();
        // Normalize: replace \r\n and literal \n sequences
        String normalized = output.replace("\r\n", "\n").replace("\r", "\n");

        // Use regex to extract sections: ===KEY=== followed by value until next ===KEY=== or end
        Pattern sectionPattern = Pattern.compile("===([A-Z]+)===\\s*\\n?(.*?)(?=\\n?===|$)", Pattern.DOTALL);
        Matcher matcher = sectionPattern.matcher(normalized);

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            parseSection(health, key, value);
        }

        // Fallback: if uptimeSeconds not set, try to parse from uptime command output
        if (!health.containsKey("uptimeSeconds") && health.containsKey("uptime")) {
            Long parsed = parseUptimeToSeconds((String) health.get("uptime"));
            if (parsed != null) {
                health.put("uptimeSeconds", parsed);
                log.debug("Fallback: parsed uptimeSeconds={} from uptime string", parsed);
            }
        }

        log.debug("Parsed health: cpu={}, mem={}, disk={}, uptimeSecs={}, os={}",
                health.get("cpuUsage"), health.get("memoryUsage"), health.get("diskUsage"),
                health.get("uptimeSeconds"), health.get("os"));

        return health;
    }

    private void parseSection(Map<String, Object> health, String key, String value) {
        switch (key) {
            case "CPU" -> {
                try {
                    health.put("cpuUsage", Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    health.put("cpuUsage", 0.0);
                }
            }
            case "MEMORY" -> {
                String cleanVal = value.replace("\\n", " ").trim();
                String[] parts = cleanVal.split("\\s+");
                if (parts.length >= 3) {
                    try {
                        health.put("memoryTotalMB", Long.parseLong(parts[0]));
                        health.put("memoryUsedMB", Long.parseLong(parts[1]));
                        health.put("memoryUsage", Double.parseDouble(parts[2]));
                    } catch (NumberFormatException e) {
                        health.put("memoryUsage", 0.0);
                    }
                }
            }
            case "DISK" -> {
                String[] parts = value.split("\\s+");
                if (parts.length >= 4) {
                    health.put("diskTotal", parts[0]);
                    health.put("diskUsed", parts[1]);
                    health.put("diskAvailable", parts[2]);
                    String percent = parts[3].replace("%", "");
                    try {
                        health.put("diskUsage", Double.parseDouble(percent));
                    } catch (NumberFormatException e) {
                        health.put("diskUsage", 0.0);
                    }
                }
            }
            case "UPTIME" -> health.put("uptime", value);
            case "LOAD" -> health.put("loadAverage", value);
            case "OS" -> health.put("os", value);
            case "SECS" -> {
                if (!value.isEmpty()) {
                    try {
                        health.put("uptimeSeconds", Long.parseLong(value));
                    } catch (NumberFormatException e) {
                        log.debug("Failed to parse SECS value: '{}'", value);
                    }
                }
            }
        }
    }

    /**
     * Parse human-readable uptime string to seconds.
     * Handles formats like:
     *   "up 3 days, 12:34:56"
     *   "up 2 weeks, 1 day, 3:45:67"
     *   "up 1 day, 2:34"
     *   "up 5:30"
     *   "up 12 min"
     */
    private Long parseUptimeToSeconds(String uptime) {
        if (uptime == null || uptime.isBlank()) return null;
        try {
            long totalSeconds = 0;
            String lower = uptime.toLowerCase().replace("up ", "").replace(",", "").trim();

            // Extract weeks
            Pattern weekPat = Pattern.compile("(\\d+)\\s*week");
            Matcher weekMatcher = weekPat.matcher(lower);
            if (weekMatcher.find()) {
                totalSeconds += Long.parseLong(weekMatcher.group(1)) * 7 * 86400;
            }

            // Extract days
            Pattern dayPat = Pattern.compile("(\\d+)\\s*day");
            Matcher dayMatcher = dayPat.matcher(lower);
            if (dayMatcher.find()) {
                totalSeconds += Long.parseLong(dayMatcher.group(1)) * 86400;
            }

            // Extract hours:minutes:seconds (e.g., "12:34:56" or "5:30")
            Pattern timePat = Pattern.compile("(\\d+):(\\d+)(?::(\\d+))?");
            Matcher timeMatcher = timePat.matcher(lower);
            if (timeMatcher.find()) {
                totalSeconds += Long.parseLong(timeMatcher.group(1)) * 3600;
                totalSeconds += Long.parseLong(timeMatcher.group(2)) * 60;
                if (timeMatcher.group(3) != null) {
                    totalSeconds += Long.parseLong(timeMatcher.group(3));
                }
            } else {
                // Try extracting just minutes (e.g., "12 min")
                Pattern minPat = Pattern.compile("(\\d+)\\s*min");
                Matcher minMatcher = minPat.matcher(lower);
                if (minMatcher.find()) {
                    totalSeconds += Long.parseLong(minMatcher.group(1)) * 60;
                }
            }

            return totalSeconds > 0 ? totalSeconds : null;
        } catch (Exception e) {
            log.debug("Failed to parse uptime string '{}': {}", uptime, e.getMessage());
            return null;
        }
    }

    private void checkThresholdsAndAlert(Server server, Map<String, Object> health) {
        double cpu = health.get("cpuUsage") instanceof Number ? ((Number) health.get("cpuUsage")).doubleValue() : 0;
        double memory = health.get("memoryUsage") instanceof Number ? ((Number) health.get("memoryUsage")).doubleValue() : 0;
        double disk = health.get("diskUsage") instanceof Number ? ((Number) health.get("diskUsage")).doubleValue() : 0;

        if (cpu > CPU_CRITICAL) {
            createAlert(server, Alert.AlertSeverity.CRITICAL, "CPU 使用率过高",
                    server.getName() + " CPU 使用率 " + String.format("%.1f", cpu) + "%，超过阈值 " + (int) CPU_CRITICAL + "%",
                    "cpu", "CPU");
        }
        if (memory > MEMORY_CRITICAL) {
            createAlert(server, Alert.AlertSeverity.CRITICAL, "内存使用率过高",
                    server.getName() + " 内存使用率 " + String.format("%.1f", memory) + "%，超过阈值 " + (int) MEMORY_CRITICAL + "%",
                    "memory", "内存");
        }
        if (disk > DISK_CRITICAL) {
            createAlert(server, Alert.AlertSeverity.CRITICAL, "磁盘空间严重不足",
                    server.getName() + " 磁盘使用率 " + String.format("%.1f", disk) + "%，超过阈值 " + (int) DISK_CRITICAL + "%",
                    "disk", "磁盘");
        } else if (disk > DISK_WARNING) {
            createAlert(server, Alert.AlertSeverity.WARNING, "磁盘空间不足",
                    server.getName() + " 磁盘使用率 " + String.format("%.1f", disk) + "%，建议清理",
                    "disk", "磁盘");
        }
    }

    private void createAlert(Server server, Alert.AlertSeverity severity, String title, String description, String source, String category) {
        // Avoid duplicate alerts within 30 minutes
        long recentCount = alertRepository.countToday();
        if (recentCount > 20) return; // Prevent alert storm

        Alert alert = Alert.builder()
                .server(server)
                .severity(severity)
                .title(title)
                .description(description)
                .source(source)
                .category(category)
                .status(Alert.AlertStatus.OPEN)
                .build();
        alertRepository.save(alert);

        // Log event
        try {
            Event event = Event.builder()
                    .source("alert")
                    .level(severity == Alert.AlertSeverity.CRITICAL ? Event.EventLevel.ERR : Event.EventLevel.WARN)
                    .message(title + ": " + description)
                    .build();
            eventRepository.save(event);
        } catch (Exception ignored) {}

        // Broadcast via WebSocket
        try {
            Event wsEvent = Event.builder()
                    .source("alert")
                    .level(severity == Alert.AlertSeverity.CRITICAL ? Event.EventLevel.ERR : Event.EventLevel.WARN)
                    .message(title)
                    .build();
            eventHandler.broadcastEvent(wsEvent);
        } catch (Exception ignored) {}

        log.warn("Alert created for {}: {}", server.getName(), title);
    }

    private void markServerOffline(Server server) {
        // Don't change server status if SSH fails — keep previous status
        // Only mark as unreachable in cache
        Map<String, Object> offlineHealth = new LinkedHashMap<>();
        offlineHealth.put("serverId", server.getId());
        offlineHealth.put("serverName", server.getName());
        offlineHealth.put("ip", server.getIp());
        offlineHealth.put("status", "UNREACHABLE");
        offlineHealth.put("checkedAt", LocalDateTime.now().toString());
        offlineHealth.put("error", "SSH 连接失败，保留上次状态");

        String cacheKey = HEALTH_CACHE_KEY + server.getId();
        redisTemplate.opsForValue().set(cacheKey, offlineHealth, 2, TimeUnit.MINUTES);

        log.warn("Server {} ({}) SSH unreachable, keeping previous status: {}",
                server.getName(), server.getIp(), server.getStatus());
    }
}
