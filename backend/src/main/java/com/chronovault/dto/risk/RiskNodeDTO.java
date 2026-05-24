package com.chronovault.dto.risk;

public record RiskNodeDTO(
    Long id,
    String name,
    Double score,
    String status
) {}
