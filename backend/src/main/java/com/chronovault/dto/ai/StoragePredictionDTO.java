package com.chronovault.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "存储用量预测 DTO")
public record StoragePredictionDTO(
    String month,
    Long predictedBytes
) {}
