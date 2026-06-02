package com.chronovault.dto.branch;

import com.chronovault.entity.ServerBranch;
import java.time.format.DateTimeFormatter;

public record ServerBranchDTO(
    Long id,
    String name,
    String description,
    Long serverId,
    Long createdFromSnapshotId,
    boolean isDefault,
    String createdAt
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static ServerBranchDTO from(ServerBranch b) {
        return new ServerBranchDTO(
            b.getId(),
            b.getName(),
            b.getDescription(),
            b.getServer().getId(),
            b.getCreatedFromSnapshot() != null ? b.getCreatedFromSnapshot().getId() : null,
            b.isDefault(),
            b.getCreatedAt() != null ? b.getCreatedAt().format(FMT) : ""
        );
    }
}