package com.chronovault.dto.alert;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建告警规则请求")
public record CreateAlertRuleRequest(
    @NotBlank String name,
    @Schema(description = "监控指标名称", example = "disk_usage")
    @NotBlank String metric,
    Integer threshold,
    @Schema(description = "持续时间（分钟）", example = "30")
    Integer durationMinutes,
    String severity,
    Boolean enabled
) {}
