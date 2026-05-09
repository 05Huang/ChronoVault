package com.chronovault.dto.risk;

public record RiskScoreDTO(
    Double overallScore,
    String level,
    String summary,
    Integer criticalCount,
    Integer warningCount,
    Integer anomalyCount
) {}
