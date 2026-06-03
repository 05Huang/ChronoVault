package com.chronovault.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新 Token 请求。
 */
public record RefreshTokenRequest(
    @NotBlank(message = "refreshToken 不能为空")
    String refreshToken
) {}
