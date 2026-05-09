package com.chronovault.dto.integration;

import jakarta.validation.constraints.NotBlank;

public record CreateIntegrationRequest(
    @NotBlank String type,
    @NotBlank String name,
    String url
) {}
