package com.chronovault.dto.storage;

public record StorageOverviewDTO(
    Long id,
    String type,
    String name,
    Long usedBytes,
    Long totalBytes,
    Double usagePercent,
    String status
) {}
