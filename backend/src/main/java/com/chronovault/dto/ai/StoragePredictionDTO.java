package com.chronovault.dto.ai;

public record StoragePredictionDTO(
    String month,
    Long predictedBytes
) {}
