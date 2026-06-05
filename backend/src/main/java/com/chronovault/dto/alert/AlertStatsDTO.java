package com.chronovault.dto.alert;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "告警统计 DTO")
public record AlertStatsDTO(
    Integer total,
    @Schema(description = "严重数量", example = "2")
    Integer critical,
    Integer predictive,
    @Schema(description = "警告信息")
    Integer warning,
    Integer openCount,
    Integer resolvedCount
) {}
