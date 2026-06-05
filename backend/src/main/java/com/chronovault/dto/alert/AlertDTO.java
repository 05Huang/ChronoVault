package com.chronovault.dto.alert;

import com.chronovault.entity.Alert;
import java.time.format.DateTimeFormatter;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "告警信息 DTO")
public record AlertDTO(
    Long id, String severity, String title, String description,
    @Schema(description = "告警来源", example = "snapshot-monitor")
    String source, String category, String rootCauseAnalysis,
    Integer storagePercent, String growthRate,
    @Schema(description = "当前状态", example = "ONLINE")
    String status, String createdAt, Boolean hasAutoFix
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    public static AlertDTO from(Alert a) {
        String createdAt = a.getCreatedAt() != null ? a.getCreatedAt().format(FMT) : "";
        return new AlertDTO(a.getId(), a.getSeverity().name(), a.getTitle(), a.getDescription(),
                a.getSource(), a.getCategory(), a.getRootCauseAnalysis(),
                a.getStoragePercent(), a.getGrowthRate(),
                a.getStatus().name(), createdAt, a.hasAutoFix());
    }
}
