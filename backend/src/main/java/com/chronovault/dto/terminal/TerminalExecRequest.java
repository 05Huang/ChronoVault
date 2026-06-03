package com.chronovault.dto.terminal;

import jakarta.validation.constraints.NotBlank;

public record TerminalExecRequest(
    @NotBlank(message = "命令不能为空") String command
) {}
