package com.chronovault.dto.branch;

import com.chronovault.entity.ServerBranch;
import java.time.format.DateTimeFormatter;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "服务器分支 DTO")
public record ServerBranchDTO(
    Long id,
    @Schema(description = "名称")
    String name,
    String description,
    @Schema(description = "服务器 ID", example = "1")
    Long serverId,
    Long createdFromSnapshotId,
    @Schema(description = "是否为默认分支", example = "true")
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