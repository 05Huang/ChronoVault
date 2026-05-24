package com.chronovault.dto.storage;

public record StorageHealthDTO(
    String status,
    String iops,
    String latency,
    String throughput,
    Integer errorCount
) {}
