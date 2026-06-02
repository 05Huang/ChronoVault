package com.chronovault.dto.team;

import jakarta.validation.constraints.NotBlank;

public record UpdateMemberRequest(
    @NotBlank(message = "角色不能为空")
    String role,
    String permissions
) {}
