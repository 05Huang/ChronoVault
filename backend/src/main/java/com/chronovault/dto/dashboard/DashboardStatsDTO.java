package com.chronovault.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "仪表盘统计数据 DTO")
public record DashboardStatsDTO(
    Integer totalServers,
    @Schema(description = "活跃服务器数", example = "8")
    Integer activeServers,
    Integer totalContainers,
    @Schema(description = "今日备份次数", example = "3")
    Integer todayBackups,
    Integer totalSnapshots,
    @Schema(description = "今日告警数", example = "2")
    Integer alertsToday,
    String recoveryRate,
    @Schema(description = "已用存储", example = "50 GB")
    String storageUsed,
    String storageTotal,
    @Schema(description = "团队成员数", example = "5")
    Integer teamMembers,
    Double uptimePercent
) {}
