package com.chronovault.dto.risk;

public record RiskTrendDTO(
    String date,
    Double stability,
    Double security
) {}
