package com.chronovault.dto.drift;

public record FileDrift(
    String filePath,
    String driftType,
    String currentHash,
    String baselineHash,
    String details
) {}