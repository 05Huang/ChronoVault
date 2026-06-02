package com.chronovault.dto.drift;

public record ContainerDrift(
    String containerName,
    String status,
    String driftType,
    String details
) {}