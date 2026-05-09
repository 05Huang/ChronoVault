package com.chronovault.dto.ai;

import com.chronovault.entity.AiInsight;

public record AiInsightDTO(
    Long id, String title, String description, String icon, String category
) {
    public static AiInsightDTO from(AiInsight i) {
        return new AiInsightDTO(i.getId(), i.getTitle(), i.getDescription(), i.getIcon(), i.getCategory());
    }
}
