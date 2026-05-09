package com.chronovault.dto.alert;

import jakarta.validation.constraints.NotBlank;

public record CreateAlertRuleRequest(
    @NotBlank String name,
    @NotBlank String metric,
    Integer threshold,
    Integer durationMinutes,
    String severity,
    Boolean enabled
) {}
