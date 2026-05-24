package com.chronovault.dto.snapshot;

import com.chronovault.entity.SnapshotTag;
import java.time.format.DateTimeFormatter;

public record SnapshotTagDTO(Long id, Long snapshotId, String name, String color, String createdAt) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static SnapshotTagDTO from(SnapshotTag tag) {
        String createdAt = tag.getCreatedAt() != null ? tag.getCreatedAt().format(FMT) : "";
        return new SnapshotTagDTO(tag.getId(), tag.getSnapshot().getId(), tag.getName(), tag.getColor(), createdAt);
    }
}
