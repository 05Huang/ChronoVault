package com.chronovault.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "风险节点 DTO")
public record RiskNodeDTO(
    Long id,
    @Schema(description = "名称")
    String name,
    Double score,
    String status
) {}
