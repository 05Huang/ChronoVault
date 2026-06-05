package com.chronovault.dto.alert;

import com.chronovault.entity.AlertRule;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "告警规则 DTO")
public record AlertRuleDTO(
    Long id, String name, String metric, Double threshold,
    @Schema(description = "持续时间（分钟）", example = "30")
    Integer durationMinutes, String severity, Boolean enabled
) {
    public static AlertRuleDTO from(AlertRule r) {
        return new AlertRuleDTO(r.getId(), r.getName(), r.getMetric(), r.getThreshold(),
                r.getDurationMinutes(), r.getSeverity().name(), r.getEnabled());
    }
}
