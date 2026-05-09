package com.chronovault.dto.alert;

import com.chronovault.entity.Alert;
import java.time.format.DateTimeFormatter;

public record AlertDTO(
    Long id, String severity, String title, String description,
    String source, String category, String rootCauseAnalysis,
    Integer storagePercent, String growthRate,
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
