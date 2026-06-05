package com.chronovault.dto.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建服务器请求")
public record CreateServerRequest(
        @NotBlank(message = "服务器名称不能为空")
        @Size(max = 100, message = "名称不能超过100个字符")
        @Schema(description = "名称")
        String name,

        @NotBlank(message = "IP地址不能为空")
        @Pattern(regexp = "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$", message = "IP地址格式不正确")
        @Schema(description = "服务器 IP 地址", example = "192.168.1.100")
        String ip,

        String os
) {}
