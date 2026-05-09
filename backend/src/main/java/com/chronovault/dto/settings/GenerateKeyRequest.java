package com.chronovault.dto.settings;

import jakarta.validation.constraints.NotBlank;

public record GenerateKeyRequest(
    @NotBlank String name,
    String scope
) {}
