package com.chronovault.dto.settings;

import java.util.Map;

public record UpdateAiConfigRequest(
    Map<String, Object> config
) {}
