package com.chronovault.dto.team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteRequest(
    @NotBlank String name,
    @Email @NotBlank String email,
    String role,
    String permissions
) {}
