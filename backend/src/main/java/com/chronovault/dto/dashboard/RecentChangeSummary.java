package com.chronovault.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "最近变更摘要 DTO")
public record RecentChangeSummary(
    Long snapshotId,
    @Schema(description = "服务器名称", example = "web-server-01")
    String serverName,
    String createdAt,
    @Schema(description = "新增包数量", example = "2")
    Integer packagesAdded,
    Integer packagesRemoved,
    @Schema(description = "升级包数量", example = "1")
    Integer packagesUpgraded,
    Integer servicesChanged,
    Integer configsChanged
) {}