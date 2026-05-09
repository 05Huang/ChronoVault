package com.chronovault.dto.dashboard;

public record ActivityTrendDTO(
    String label,
    Integer snapshots,
    Integer alerts,
    Integer recoveries
) {}
