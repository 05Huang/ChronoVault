package com.chronovault.dto.settings;

import com.chronovault.entity.AuditLog;
import java.time.format.DateTimeFormatter;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "审计日志 DTO")
public record AuditLogDTO(
    Long id, String action, String icon, String ipAddress, String userAgent, String createdAt,
    @Schema(description = "操作用户名")
    String userName,
    @Schema(description = "服务器名称")
    String serverName,
    @Schema(description = "变更类型")
    String changeType,
    @Schema(description = "资源类型")
    String resourceType
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    public static AuditLogDTO from(AuditLog l) {
        String createdAt = l.getCreatedAt() != null ? l.getCreatedAt().format(FMT) : "";
        String userName = l.getUser() != null ? l.getUser().getName() : null;
        String serverName = l.getServer() != null ? l.getServer().getName() : null;
        return new AuditLogDTO(l.getId(), l.getAction(), l.getIcon(), l.getIpAddress(), l.getUserAgent(), createdAt,
                userName, serverName, l.getChangeType(), l.getResourceType());
    }
}
