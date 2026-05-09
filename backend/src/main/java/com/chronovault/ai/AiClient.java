package com.chronovault.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final boolean enabled;

    public AiClient(
            @Value("${chronovault.ai.base-url:https://api.xiaomimimo.com/v1}") String baseUrl,
            @Value("${chronovault.ai.api-key:}") String apiKey,
            @Value("${chronovault.ai.model:mimo-v2.5-pro}") String model,
            @Value("${chronovault.ai.max-tokens:4096}") int maxTokens,
            @Value("${chronovault.ai.temperature:0.7}") double temperature,
            @Value("${chronovault.ai.enabled:true}") boolean enabled,
            ObjectMapper objectMapper) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.enabled = enabled;
        this.objectMapper = objectMapper;

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey != null ? apiKey : "")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String chat(String systemPrompt, String userPrompt) {
        if (!enabled) {
            log.debug("AI disabled, returning null");
            return null;
        }

        try {
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

            String responseJson = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson == null) return null;

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText();
            }

            return null;
        } catch (Exception e) {
            log.error("AI API call failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
