package com.chronovault.dto.dashboard;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "仪表盘概览 DTO")
public record DashboardOverviewDTO(
    List<ServerSnapshotStatus> serverStatuses,
    @Schema(description = "最近变更列表")
    List<RecentChangeSummary> recentChanges,
    PendingAlertsInfo pendingAlerts,
    RecentRollbackInfo recentRollback
) {}