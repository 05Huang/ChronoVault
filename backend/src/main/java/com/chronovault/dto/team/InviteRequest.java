package com.chronovault.dto.team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "邀请团队成员请求")
public record InviteRequest(
    @NotBlank String name,
    @Schema(description = "用户邮箱地址", example = "admin@chronovault.com")
    @Email @NotBlank String email,
    String role,
    String permissions
) {}
