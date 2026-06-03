package com.chronovault.dto.dashboard;

public record ServerSnapshotStatus(
    Long serverId,
    String serverName,
    String lastSnapshotTime,
    String timeSinceLastSnapshot,
    boolean isStale,
    String lastChangeSummary
) {}