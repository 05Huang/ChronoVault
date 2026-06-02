package com.chronovault.dto.snapshot;

public record SnapshotVerifyResult(
    Long snapshotId,
    boolean verified,
    String errors,
    long durationMs
) {}