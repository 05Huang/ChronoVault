package com.chronovault.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "风险评分 DTO")
public record RiskScoreDTO(
    Double overallScore,
    @Schema(description = "风险等级/日志级别", example = "HIGH")
    String level,
    String summary,
    Integer criticalCount,
    Integer warningCount,
    Integer anomalyCount
) {}
