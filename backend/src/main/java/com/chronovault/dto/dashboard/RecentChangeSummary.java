package com.chronovault.dto.dashboard;

public record RecentChangeSummary(
    Long snapshotId,
    String serverName,
    String createdAt,
    Integer packagesAdded,
    Integer packagesRemoved,
    Integer packagesUpgraded,
    Integer servicesChanged,
    Integer configsChanged
) {}