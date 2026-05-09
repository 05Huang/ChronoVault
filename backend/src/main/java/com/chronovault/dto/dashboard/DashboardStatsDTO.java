package com.chronovault.dto.dashboard;

public record DashboardStatsDTO(
    Integer totalServers,
    Integer activeServers,
    Integer totalContainers,
    Integer todayBackups,
    Integer totalSnapshots,
    Integer alertsToday,
    String recoveryRate,
    String storageUsed,
    String storageTotal,
    Integer teamMembers,
    Double uptimePercent
) {}
