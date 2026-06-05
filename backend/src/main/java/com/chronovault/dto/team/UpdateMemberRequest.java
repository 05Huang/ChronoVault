package com.chronovault.dto.team;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "更新成员角色请求")
public record UpdateMemberRequest(
    @NotBlank(message = "角色不能为空")
    @Schema(description = "用户角色（OWNER/ADMIN/MEMBER/VIEWER）", example = "ADMIN")
    String role,
    String permissions
) {}
