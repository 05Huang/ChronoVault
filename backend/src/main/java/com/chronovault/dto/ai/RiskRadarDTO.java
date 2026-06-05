package com.chronovault.dto.ai;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "风险雷达图 DTO")
public record RiskRadarDTO(
    List<Map<String, Object>> indicators,
    List<Double> values
) {}
