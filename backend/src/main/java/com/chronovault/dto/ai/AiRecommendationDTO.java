package com.chronovault.dto.ai;

import com.chronovault.entity.AiRecommendation;

public record AiRecommendationDTO(
    Long id, String title, String description, String icon, String impact, Boolean applied
) {
    public static AiRecommendationDTO from(AiRecommendation r) {
        return new AiRecommendationDTO(r.getId(), r.getTitle(), r.getDescription(),
                r.getIcon(), r.getImpact(), r.getApplied());
    }
}
