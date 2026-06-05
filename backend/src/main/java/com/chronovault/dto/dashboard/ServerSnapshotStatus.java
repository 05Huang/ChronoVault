package com.chronovault.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "服务器快照状态 DTO")
public record ServerSnapshotStatus(
    Long serverId,
    @Schema(description = "服务器名称", example = "web-server-01")
    String serverName,
    String lastSnapshotTime,
    @Schema(description = "距上次快照时长", example = "2小时")
    String timeSinceLastSnapshot,
    boolean isStale,
    String lastChangeSummary
) {}