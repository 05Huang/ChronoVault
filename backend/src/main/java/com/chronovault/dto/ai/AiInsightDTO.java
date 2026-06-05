package com.chronovault.dto.ai;

import com.chronovault.entity.AiInsight;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 洞察 DTO")
public record AiInsightDTO(
    Long id, String title, String description, String icon, String category
) {
    public static AiInsightDTO from(AiInsight i) {
        return new AiInsightDTO(i.getId(), i.getTitle(), i.getDescription(), i.getIcon(), i.getCategory());
    }
}
