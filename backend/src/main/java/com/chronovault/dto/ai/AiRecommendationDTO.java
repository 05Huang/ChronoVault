package com.chronovault.dto.ai;

import com.chronovault.entity.AiRecommendation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 建议 DTO")
public record AiRecommendationDTO(
    Long id, String title, String description, String icon, String impact, Boolean applied
) {
    public static AiRecommendationDTO from(AiRecommendation r) {
        return new AiRecommendationDTO(r.getId(), r.getTitle(), r.getDescription(),
                r.getIcon(), r.getImpact(), r.getApplied());
    }
}
