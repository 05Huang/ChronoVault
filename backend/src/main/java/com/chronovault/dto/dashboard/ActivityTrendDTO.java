package com.chronovault.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "活动趋势 DTO")
public record ActivityTrendDTO(
    String label,
    @Schema(description = "快照数量", example = "15")
    Integer snapshots,
    Integer alerts,
    Integer recoveries
) {}
