package com.chronovault.dto.server;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "日志条目 DTO")
public record LogEntryDTO(
    String timestamp,
    @Schema(description = "风险等级/日志级别", example = "HIGH")
    String level,
    String message,
    String source
) {}
