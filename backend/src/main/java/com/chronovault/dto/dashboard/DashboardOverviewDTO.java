package com.chronovault.dto.dashboard;

import java.util.List;

public record DashboardOverviewDTO(
    List<ServerSnapshotStatus> serverStatuses,
    List<RecentChangeSummary> recentChanges,
    PendingAlertsInfo pendingAlerts,
    RecentRollbackInfo recentRollback
) {}