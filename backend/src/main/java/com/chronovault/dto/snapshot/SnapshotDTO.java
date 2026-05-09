package com.chronovault.dto.snapshot;

import com.chronovault.entity.Snapshot;
import java.time.format.DateTimeFormatter;

public record SnapshotDTO(Long id, String name, String createdAt, String status, String description,
                          String hash, Integer microserviceCount, String serverName, Long sizeBytes, String warning) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static SnapshotDTO from(Snapshot s) {
        String createdAt = s.getCreatedAt() != null ? s.getCreatedAt().format(FMT) : "";
        String warning = s.getStatus() == Snapshot.SnapshotStatus.WARNING ? "发现未同步的复制槽" : null;
        String serverName = s.getServer() != null ? s.getServer().getName() : null;
        return new SnapshotDTO(s.getId(), s.getTitle(), createdAt, s.getStatus().name(),
                s.getDescription(), s.getHash(), s.getMicroserviceCount(), serverName, s.getSizeBytes(), warning);
    }
}
