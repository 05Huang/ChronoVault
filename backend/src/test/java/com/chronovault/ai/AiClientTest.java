package com.chronovault.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

class AiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chat_disabled_returnsNull() {
        AiClient disabledClient = new AiClient(
                "http://localhost:9999/v1", "key", "model", 1024, 0.7, false, objectMapper);

        String result = disabledClient.chat("system", "user");
        assertNull(result);
    }

    @Test
    void isEnabled_reflectsConfig() {
        AiClient enabled = new AiClient("http://localhost:9999/v1", "key", "model", 1024, 0.7, true, objectMapper);
        AiClient disabled = new AiClient("http://localhost:9999/v1", "key", "model", 1024, 0.7, false, objectMapper);

        assertTrue(enabled.isEnabled());
        assertFalse(disabled.isEnabled());
    }

    @Test
    void chat_unreachableServer_returnsNull() {
        // Point to a non-existent server - should fail gracefully
        AiClient client = new AiClient(
                "http://127.0.0.1:1/v1", "key", "model", 1024, 0.7, true, objectMapper);

        String result = client.chat("system", "user");
        assertNull(result);
    }

    @Test
    void constructor_setsAllParameters() {
        AiClient client = new AiClient(
                "http://test.com/v1", "my-key", "my-model", 2048, 0.3, true, objectMapper);

        assertTrue(client.isEnabled());
        // Verify through isEnabled - the constructor doesn't throw
    }

    @Test
    void constructor_withNullApiKey_doesNotThrow() {
        assertDoesNotThrow(() ->
                new AiClient("http://test.com/v1", null, "model", 1024, 0.7, true, objectMapper));
    }

    @Test
    void constructor_withEmptyApiKey_doesNotThrow() {
        assertDoesNotThrow(() ->
                new AiClient("http://test.com/v1", "", "model", 1024, 0.7, true, objectMapper));
    }
}
