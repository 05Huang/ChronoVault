package com.chronovault.dto.alert;

import com.chronovault.entity.AlertRule;

public record AlertRuleDTO(
    Long id, String name, String metric, Double threshold,
    Integer durationMinutes, String severity, Boolean enabled
) {
    public static AlertRuleDTO from(AlertRule r) {
        return new AlertRuleDTO(r.getId(), r.getName(), r.getMetric(), r.getThreshold(),
                r.getDurationMinutes(), r.getSeverity().name(), r.getEnabled());
    }
}
