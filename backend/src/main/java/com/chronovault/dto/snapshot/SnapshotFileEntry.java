package com.chronovault.dto.snapshot;

public record SnapshotFileEntry(
    String path,
    String name,
    long size,
    String type,
    String modifiedAt
) {}