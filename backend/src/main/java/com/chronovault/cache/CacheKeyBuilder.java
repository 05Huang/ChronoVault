package com.chronovault.cache;

import java.time.Duration;

/**
 * Unified cache key builder for Redis caching across the application.
 * Provides consistent key prefixes and TTL management.
 */
public final class CacheKeyBuilder {

    private CacheKeyBuilder() {}

    // Global prefix (also applied by CacheService)
    public static final String PREFIX = "cv:";

    // ---- Dashboard ----
    public static final Duration DASHBOARD_STATS_TTL = Duration.ofMinutes(5);
    public static final Duration DASHBOARD_TOPOLOGY_TTL = Duration.ofSeconds(30);
    public static final Duration DASHBOARD_OVERVIEW_TTL = Duration.ofSeconds(60);

    public static String dashboardStats() { return "dashboard:stats"; }
    public static String dashboardTopology() { return "dashboard:topology"; }
    public static String dashboardOverview() { return "dashboard:overview"; }

    // ---- Server ----
    public static final Duration SERVERS_TTL = Duration.ofSeconds(30);

    public static String servers(String email) { return "servers:" + email; }

    // ---- Storage ----
    public static final Duration STORAGE_HEALTH_TTL = Duration.ofMinutes(2);

    public static String storageHealth(Long storageId) { return "storage:health:" + storageId; }

    // ---- AI ----
    public static final Duration AI_CACHE_TTL = Duration.ofMinutes(10);

    public static String aiRiskRadar() { return "ai:risk-radar"; }
    public static String aiStoragePrediction() { return "ai:storage-prediction"; }

    // ---- Agent ----
    public static final Duration AGENT_HEARTBEAT_TTL = Duration.ofSeconds(120);

    public static String agentHeartbeat(Long serverId) { return "agent:" + serverId + ":heartbeat"; }

    // ---- Snapshot ----
    public static final Duration SNAPSHOT_CACHE_TTL = Duration.ofSeconds(30);

    public static String snapshotList(String email) { return "snapshots:" + email; }
}