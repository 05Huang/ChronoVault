package com.chronovault.dto.risk;

public record RiskNodeDTO(
    String name,
    Double score,
    String status
) {}
