package com.chronovault.dto.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "克隆服务器请求")
public record CloneServerRequest(
    @NotNull Long sourceServerId,
    @NotBlank(message = "目标服务器IP不能为空")
    @Schema(description = "目标服务器 IP", example = "192.168.1.101")
    String targetServerIp,
    String targetName,
    @Schema(description = "目标 SSH 端口", example = "22")
    Integer targetSshPort,
    String targetSshUsername
) {}