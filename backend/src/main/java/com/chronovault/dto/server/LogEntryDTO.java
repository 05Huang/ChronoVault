package com.chronovault.dto.server;

public record LogEntryDTO(
    String timestamp,
    String level,
    String message,
    String source
) {}
