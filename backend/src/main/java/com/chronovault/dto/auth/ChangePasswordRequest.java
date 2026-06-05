package com.chronovault.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 修改密码请求。
 */
@Schema(description = "修改密码请求")
public record ChangePasswordRequest(
    @NotBlank(message = "旧密码不能为空")
    @Schema(description = "当前密码")
    String oldPassword,
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码长度不能少于6位")
    String newPassword
) {}
