package com.chronovault.dto.risk;

import com.chronovault.entity.Risk;
import java.time.format.DateTimeFormatter;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "风险项 DTO")
public record RiskDTO(
    Long id, String level, String title, String description,
    @Schema(description = "告警分类", example = "STORAGE")
    String category, String aiSuggestion, String actionText,
    String status, String discoveredAt
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    public static RiskDTO from(Risk r) {
        String discoveredAt = r.getDiscoveredAt() != null ? r.getDiscoveredAt().format(FMT) : "";
        return new RiskDTO(r.getId(), r.getLevel().name(), r.getTitle(), r.getDescription(),
                r.getCategory(), r.getAiSuggestion(), r.getActionText(),
                r.getStatus().name(), discoveredAt);
    }
}
