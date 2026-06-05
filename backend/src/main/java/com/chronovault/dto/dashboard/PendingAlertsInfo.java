package com.chronovault.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "待处理告警信息 DTO")
public record PendingAlertsInfo(
    Integer totalPending,
    @Schema(description = "高风险数量", example = "1")
    Integer highRisk,
    Integer warnings
) {}