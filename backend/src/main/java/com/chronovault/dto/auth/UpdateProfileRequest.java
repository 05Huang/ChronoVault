package com.chronovault.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新用户资料请求。
 */
public record UpdateProfileRequest(
    @NotBlank(message = "姓名不能为空")
    String name
) {}
