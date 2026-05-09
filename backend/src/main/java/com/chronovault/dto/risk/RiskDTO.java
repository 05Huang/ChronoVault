package com.chronovault.dto.risk;

import com.chronovault.entity.Risk;
import java.time.format.DateTimeFormatter;

public record RiskDTO(
    Long id, String level, String title, String description,
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
