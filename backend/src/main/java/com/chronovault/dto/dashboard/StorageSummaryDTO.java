package com.chronovault.dto.dashboard;

public record StorageSummaryDTO(
    String type,
    String name,
    Long usedBytes,
    Long totalBytes,
    Double usagePercent
) {}
