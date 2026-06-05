package com.chronovault.ai;

import com.chronovault.entity.SystemSetting;
import com.chronovault.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiClient {

    private final SystemSettingRepository settingRepository;
    private final ObjectMapper objectMapper;

    // Cached config
    private volatile boolean enabled;
    private volatile String baseUrl;
    private volatile String apiKey;
    private volatile String model;
    private volatile int maxTokens;
    private volatile double temperature;
    private volatile Instant lastLoad = Instant.EPOCH;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    public AiClient(SystemSettingRepository settingRepository, ObjectMapper objectMapper) {
        this.settingRepository = settingRepository;
        this.objectMapper = objectMapper;
        reloadConfig();
    }

    public synchronized void reloadConfig() {
        try {
            this.enabled = getSetting("ai.enabled", "true").equalsIgnoreCase("true");
            this.baseUrl = getSetting("ai.base-url", "https://api.xiaomimimo.com/v1");
            this.apiKey = getSetting("ai.api-key", "");
            this.model = getSetting("ai.model", "mimo-v2.5-pro");
            this.maxTokens = Integer.parseInt(getSetting("ai.max-tokens", "4096"));
            this.temperature = Double.parseDouble(getSetting("ai.temperature", "0.7"));
            this.lastLoad = Instant.now();
            log.info("AI config reloaded: enabled={}, model={}, baseUrl={}", enabled, model, baseUrl);
        } catch (Exception e) {
            log.warn("Failed to reload AI config from DB, keeping current values: {}", e.getMessage());
        }
    }

    private String getSetting(String key, String defaultValue) {
        return settingRepository.findById(key)
                .map(SystemSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultValue);
    }

    private void ensureConfigFresh() {
        if (Instant.now().isAfter(lastLoad.plus(CACHE_TTL))) {
            reloadConfig();
        }
    }

    public String chat(String systemPrompt, String userPrompt) {
        ensureConfigFresh();

        if (!enabled) {
            log.debug("AI disabled, returning null");
            return null;
        }

        // Log request (mask sensitive data)
        log.info("[AI_REQUEST] model={}, systemPrompt={} chars, userPrompt={} chars",
                model,
                systemPrompt != null ? systemPrompt.length() : 0,
                userPrompt != null ? userPrompt.length() : 0);

        try {
            WebClient client = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("api-key", apiKey != null ? apiKey : "")
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "max_completion_tokens", maxTokens,
                    "temperature", temperature,
                    "top_p", 0.95,
                    "stream", false
            );

            String responseJson = client.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Log response (mask sensitive data)
            log.info("[AI_RESPONSE] status={}, responseLength={} chars",
                    responseJson != null ? "ok" : "null",
                    responseJson != null ? responseJson.length() : 0);

            if (responseJson == null) {
                log.warn("AI API returned null response");
                return null;
            }

            JsonNode root = objectMapper.readTree(responseJson);

            // Check for API error
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                log.warn("[AI_RESPONSE] API error: {}", error.path("message").asText("unknown"));
                return null;
            }

            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                String content = message.path("content").asText("");
                // MiMo reasoning models: if content is empty, fall back to reasoning_content
                if (content.isBlank()) {
                    String reasoning = message.path("reasoning_content").asText("");
                    if (!reasoning.isBlank()) {
                        log.debug("[AI_RESPONSE] Using reasoning_content as fallback ({} chars)", reasoning.length());
                        return reasoning;
                    }
                    log.warn("[AI_RESPONSE] Empty content and empty reasoning_content");
                }
                log.debug("[AI_RESPONSE] Success: {} chars", content.length());
                return content.isBlank() ? null : content;
            }

            log.warn("[AI_RESPONSE] No choices in response: {}", responseJson.substring(0, Math.min(200, responseJson.length())));
            return null;
        } catch (Exception e) {
            log.error("[AI_RESPONSE] API call failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        ensureConfigFresh();
        return enabled;
    }

    public String getModelName() {
        ensureConfigFresh();
        return model;
    }
}
