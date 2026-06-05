package com.chronovault.dto.auth;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 刷新 Token 请求。
 */
@Schema(description = "刷新令牌请求")
public record RefreshTokenRequest(
    @NotBlank(message = "refreshToken 不能为空")
    String refreshToken
) {}
