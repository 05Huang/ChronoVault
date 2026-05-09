package com.chronovault.dto.dashboard;

public record AnomalyDTO(
    Long id,
    String severity,
    String title,
    String source,
    String time
) {}
