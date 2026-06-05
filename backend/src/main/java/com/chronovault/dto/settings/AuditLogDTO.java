package com.chronovault.dto.settings;

import com.chronovault.entity.AuditLog;
import java.time.format.DateTimeFormatter;

public record AuditLogDTO(
    Long id, String action, String icon, String ipAddress, String userAgent, String createdAt
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    public static AuditLogDTO from(AuditLog l) {
        String createdAt = l.getCreatedAt() != null ? l.getCreatedAt().format(FMT) : "";
        return new AuditLogDTO(l.getId(), l.getAction(), l.getIcon(), l.getIpAddress(), l.getUserAgent(), createdAt);
    }
}
