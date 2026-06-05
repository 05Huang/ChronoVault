package com.chronovault.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "风险趋势 DTO")
public record RiskTrendDTO(
    String date,
    @Schema(description = "稳定性评分", example = "90.0")
    Double stability,
    Double security
) {}
