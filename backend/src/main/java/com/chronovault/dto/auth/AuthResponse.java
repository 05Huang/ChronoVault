package com.chronovault.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "认证响应（包含访问令牌和刷新令牌）")
public record AuthResponse(String token, String refreshToken, UserDTO user) {}
