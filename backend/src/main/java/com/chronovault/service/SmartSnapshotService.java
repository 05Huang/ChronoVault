package com.chronovault.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chronovault.entity.Snapshot;
import com.chronovault.repository.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Smart Snapshot Scheduler: analyzes historical change patterns per server
 * to dynamically adjust auto-snapshot frequency.
 *
 * - High change velocity → shorter cooldown, lower drift threshold (snapshot more often)
 * - Low change velocity → longer cooldown, higher drift threshold (snapshot less often)
 *
 * Aligned with Backrest's approach of adaptive backup scheduling based on change detection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartSnapshotService {

    private final SnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    // Adaptive parameter bounds
    private static final int MIN_COOLDOWN_MINUTES = 15;
    private static final int MAX_COOLDOWN_MINUTES = 360; // 6 hours
    private static final int MIN_DRIFT_THRESHOLD = 1;
    private static final int MAX_DRIFT_THRESHOLD = 10;

    // Change velocity thresholds (changes per day)
    private static final double HIGH_VELOCITY = 10.0;   // ≥10 changes/day = very active
    private static final double LOW_VELOCITY = 1.0;      // ≤1 change/day = quiet
    private static final int ANALYSIS_WINDOW_DAYS = 7;

    /**
     * Result of smart snapshot analysis for a single server.
     */
    public record SmartSnapshotConfig(
            long serverId,
            double changeVelocity,        // average changes per day
            int snapshotFrequencyPerWeek, // snapshots created in last 7 days
            int adaptiveCooldownMinutes,  // recommended cooldown
            int adaptiveDriftThreshold,   // recommended drift threshold
            String velocityLevel,         // HIGH / NORMAL / LOW
            String recommendation         // human-readable recommendation
    ) {}

    /**
     * Analyze a server's change patterns and return adaptive snapshot configuration.
     */
    public SmartSnapshotConfig analyzeServer(long serverId) {
        LocalDateTime windowStart = LocalDateTime.now().minusDays(ANALYSIS_WINDOW_DAYS);

        // Get recent snapshots for this server (most recent first, limited to 50)
        List<Snapshot> recentSnapshots = snapshotRepository.findByServerIdOrderByCreatedAtDesc(serverId)
                .stream()
                .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().isAfter(windowStart))
                .filter(s -> s.getType() != Snapshot.SnapshotType.STASH)
                .toList();

        // Calculate change velocity from changeSummaryJson
        double totalChanges = 0;
        for (Snapshot snap : recentSnapshots) {
            totalChanges += extractChangeCount(snap.getChangeSummaryJson());
        }

        long daysInWindow = Math.max(1, Duration.between(windowStart, LocalDateTime.now()).toDays());
        double changeVelocity = totalChanges / daysInWindow;

        // Calculate snapshot frequency
        int snapshotFrequencyPerWeek = recentSnapshots.size();

        // Compute adaptive parameters
        int adaptiveCooldown = computeAdaptiveCooldown(changeVelocity);
        int adaptiveThreshold = computeAdaptiveThreshold(changeVelocity);

        // Determine velocity level
        String velocityLevel;
        String recommendation;
        if (changeVelocity >= HIGH_VELOCITY) {
            velocityLevel = "HIGH";
            recommendation = String.format("变更频繁 (%.1f 次/天)，建议每 %d 分钟检查一次，变更阈值设为 %d",
                    changeVelocity, adaptiveCooldown, adaptiveThreshold);
        } else if (changeVelocity <= LOW_VELOCITY) {
            velocityLevel = "LOW";
            recommendation = String.format("变更较少 (%.1f 次/天)，建议每 %d 分钟检查一次以节省资源",
                    changeVelocity, adaptiveCooldown);
        } else {
            velocityLevel = "NORMAL";
            recommendation = String.format("变更适中 (%.1f 次/天)，使用平衡策略：每 %d 分钟检查，阈值 %d",
                    changeVelocity, adaptiveCooldown, adaptiveThreshold);
        }

        log.debug("[SMART_SNAP] [server={}] velocity={}/day, freq={}/week, cooldown={}min, threshold={}",
                serverId, String.format("%.1f", changeVelocity), snapshotFrequencyPerWeek, adaptiveCooldown, adaptiveThreshold);

        return new SmartSnapshotConfig(
                serverId, changeVelocity, snapshotFrequencyPerWeek,
                adaptiveCooldown, adaptiveThreshold, velocityLevel, recommendation
        );
    }

    /**
     * Get smart snapshot configs for all servers with auto-snapshot enabled.
     */
    public Map<Long, SmartSnapshotConfig> analyzeAllServers() {
        // This is called by the auto-snapshot scheduler
        Map<Long, SmartSnapshotConfig> configs = new LinkedHashMap<>();
        // We'll get the server IDs from the snapshot repository
        // For now, return empty - the caller will pass server IDs
        return configs;
    }

    /**
     * Compute adaptive cooldown based on change velocity.
     * High velocity → short cooldown (check more often)
     * Low velocity → long cooldown (check less often)
     */
    private int computeAdaptiveCooldown(double changeVelocity) {
        if (changeVelocity >= HIGH_VELOCITY) {
            return MIN_COOLDOWN_MINUTES;
        }
        if (changeVelocity <= LOW_VELOCITY) {
            return MAX_COOLDOWN_MINUTES;
        }
        // Linear interpolation between bounds
        double ratio = (changeVelocity - LOW_VELOCITY) / (HIGH_VELOCITY - LOW_VELOCITY);
        return (int) Math.round(MAX_COOLDOWN_MINUTES - ratio * (MAX_COOLDOWN_MINUTES - MIN_COOLDOWN_MINUTES));
    }

    /**
     * Compute adaptive drift threshold based on change velocity.
     * High velocity → low threshold (trigger snapshot on fewer changes)
     * Low velocity → high threshold (require more changes before snapshot)
     */
    private int computeAdaptiveThreshold(double changeVelocity) {
        if (changeVelocity >= HIGH_VELOCITY) {
            return MIN_DRIFT_THRESHOLD;
        }
        if (changeVelocity <= LOW_VELOCITY) {
            return MAX_DRIFT_THRESHOLD;
        }
        // Linear interpolation
        double ratio = (changeVelocity - LOW_VELOCITY) / (HIGH_VELOCITY - LOW_VELOCITY);
        return (int) Math.round(MAX_DRIFT_THRESHOLD - ratio * (MAX_DRIFT_THRESHOLD - MIN_DRIFT_THRESHOLD));
    }

    /**
     * Extract total change count from changeSummaryJson.
     * The JSON format is: { "packagesAdded": N, "packagesRemoved": N, "packagesUpgraded": N,
     *   "servicesChanged": N, "portsChanged": N, "configsChanged": N, ... }
     */
    private int extractChangeCount(String changeSummaryJson) {
        if (changeSummaryJson == null || changeSummaryJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(changeSummaryJson);
            int count = 0;
            count += node.path("packagesAdded").asInt(0);
            count += node.path("packagesRemoved").asInt(0);
            count += node.path("packagesUpgraded").asInt(0);
            count += node.path("servicesChanged").asInt(0);
            count += node.path("portsChanged").asInt(0);
            count += node.path("configsChanged").asInt(0);
            count += node.path("dockerChanged").asInt(0);
            count += node.path("crontabChanged").asInt(0);
            return count;
        } catch (Exception e) {
            log.debug("Failed to parse changeSummaryJson: {}", e.getMessage());
            return 0;
        }
    }
}
