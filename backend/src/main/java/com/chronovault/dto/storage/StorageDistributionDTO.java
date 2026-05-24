package com.chronovault.dto.storage;

public record StorageDistributionDTO(
    String name,
    Long sizeBytes,
    Double percent,
    String type
) {}
