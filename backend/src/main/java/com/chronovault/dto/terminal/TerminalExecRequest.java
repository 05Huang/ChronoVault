package com.chronovault.dto.terminal;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "终端命令执行请求")
public record TerminalExecRequest(
    @NotBlank(message = "命令不能为空") String command
) {}
