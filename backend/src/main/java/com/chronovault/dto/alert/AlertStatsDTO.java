package com.chronovault.dto.alert;

public record AlertStatsDTO(
    Integer total,
    Integer critical,
    Integer predictive,
    Integer warning,
    Integer openCount,
    Integer resolvedCount
) {}
