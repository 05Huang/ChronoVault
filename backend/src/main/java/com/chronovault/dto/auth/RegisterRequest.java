package com.chronovault.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户注册请求")
public record RegisterRequest(
    @NotBlank(message = "姓名不能为空") String name,
    @Schema(description = "用户邮箱地址", example = "admin@chronovault.com")
    @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
    @NotBlank(message = "密码不能为空") @Size(min = 8, message = "密码长度不能少于8位") String password
) {}
