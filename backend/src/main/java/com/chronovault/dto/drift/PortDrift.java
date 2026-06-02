package com.chronovault.dto.drift;

public record PortDrift(
    int port,
    String protocol,
    String driftType,
    String details
) {}