package com.chronovault.dto.snapshot;

import com.chronovault.entity.Snapshot;

import java.time.format.DateTimeFormatter;
import java.util.List;

public record SnapshotDTO(Long id, String name, String createdAt, String status, String description,
                          String hash, Integer microserviceCount, String serverName, Long sizeBytes, String warning,
                          List<SnapshotTagDTO> tags, String stateJson, String changeSummaryJson) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Detail view — includes stateJson and changeSummaryJson */
    public static SnapshotDTO from(Snapshot s, List<SnapshotTagDTO> tags, boolean includeState) {
        String createdAt = s.getCreatedAt() != null ? s.getCreatedAt().format(FMT) : "";
        String warning = s.getStatus() == Snapshot.SnapshotStatus.WARNING ? "发现未同步的复制槽" : null;
        String serverName = s.getServer() != null ? s.getServer().getName() : null;
        String state = includeState ? s.getStateJson() : null;
        String summary = includeState ? s.getChangeSummaryJson() : null;
        return new SnapshotDTO(s.getId(), s.getTitle(), createdAt, s.getStatus().name(),
                s.getDescription(), s.getHash(), s.getMicroserviceCount(), serverName, s.getSizeBytes(), warning,
                tags != null ? tags : List.of(), state, summary);
    }

    /** List view — no stateJson (performance) */
    public static SnapshotDTO from(Snapshot s) {
        return from(s, List.of(), false);
    }

    /** List view with tags — no stateJson (performance) */
    public static SnapshotDTO from(Snapshot s, List<SnapshotTagDTO> tags) {
        return from(s, tags, false);
    }

    /** Detail view — includes stateJson */
    public static SnapshotDTO fromDetail(Snapshot s, List<SnapshotTagDTO> tags) {
        return from(s, tags, true);
    }
}
