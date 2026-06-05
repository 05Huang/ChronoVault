package com.chronovault.dto.auth;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新用户资料请求。
 */
@Schema(description = "更新用户资料请求")
public record UpdateProfileRequest(
    @NotBlank(message = "姓名不能为空")
    String name
) {}
