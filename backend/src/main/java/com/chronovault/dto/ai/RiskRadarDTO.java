package com.chronovault.dto.ai;

import java.util.List;
import java.util.Map;

public record RiskRadarDTO(
    List<Map<String, Object>> indicators,
    List<Double> values
) {}
