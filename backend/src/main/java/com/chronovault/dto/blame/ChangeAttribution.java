package com.chronovault.dto.blame;

import com.chronovault.entity.AuditLog;
import java.time.format.DateTimeFormatter;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "变更归属（Blame）信息 DTO")
public record ChangeAttribution(
    Long id,
    @Schema(description = "用户 ID", example = "1")
    Long userId,
    String userName,
    @Schema(description = "操作类型", example = "SNAPSHOT_CREATE")
    String action,
    String changeType,
    @Schema(description = "服务器 ID", example = "1")
    Long serverId,
    String serverName,
    @Schema(description = "快照 ID", example = "1")
    Long snapshotId,
    String snapshotName,
    @Schema(description = "资源 ID", example = "1")
    Long resourceId,
    String details,
    String timestamp
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static ChangeAttribution from(AuditLog auditEntry) {
        return new ChangeAttribution(
            auditEntry.getId(),
            auditEntry.getUser() != null ? auditEntry.getUser().getId() : null,
            auditEntry.getUser() != null ? auditEntry.getUser().getName() : "系统",
            auditEntry.getAction(),
            auditEntry.getChangeType(),
            auditEntry.getServer() != null ? auditEntry.getServer().getId() : null,
            auditEntry.getServer() != null ? auditEntry.getServer().getName() : null,
            auditEntry.getSnapshot() != null ? auditEntry.getSnapshot().getId() : null,
            auditEntry.getSnapshot() != null ? auditEntry.getSnapshot().getTitle() : null,
            auditEntry.getResourceId(),
            auditEntry.getDetails(),
            auditEntry.getCreatedAt() != null ? auditEntry.getCreatedAt().format(FMT) : ""
        );
    }
}