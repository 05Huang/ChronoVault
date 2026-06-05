package com.chronovault.ai;

import com.chronovault.entity.SystemSetting;
import com.chronovault.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiClientTest {

    @Mock private SystemSettingRepository settingRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private AiClient aiClient;

    private SystemSetting setting(String key, String value) {
        return SystemSetting.builder().key(key).value(value).build();
    }

    @BeforeEach
    void setUp() {
        when(settingRepository.findById("ai.enabled")).thenReturn(Optional.of(setting("ai.enabled", "false")));
        when(settingRepository.findById("ai.base-url")).thenReturn(Optional.of(setting("ai.base-url", "https://api.test.com/v1")));
        when(settingRepository.findById("ai.api-key")).thenReturn(Optional.of(setting("ai.api-key", "test-key")));
        when(settingRepository.findById("ai.model")).thenReturn(Optional.of(setting("ai.model", "test-model")));
        when(settingRepository.findById("ai.max-tokens")).thenReturn(Optional.of(setting("ai.max-tokens", "4096")));
        when(settingRepository.findById("ai.temperature")).thenReturn(Optional.of(setting("ai.temperature", "0.7")));
    }

    @Test
    void chat_disabled_returnsNull() {
        when(settingRepository.findById("ai.enabled")).thenReturn(Optional.of(setting("ai.enabled", "false")));
        aiClient.reloadConfig();
        String result = aiClient.chat("system", "user");
        assertNull(result);
    }

    @Test
    void isEnabled_afterReload_returnsValue() {
        when(settingRepository.findById("ai.enabled")).thenReturn(Optional.of(setting("ai.enabled", "false")));
        aiClient.reloadConfig();
        assertFalse(aiClient.isEnabled());

        when(settingRepository.findById("ai.enabled")).thenReturn(Optional.of(setting("ai.enabled", "true")));
        aiClient.reloadConfig();
        assertTrue(aiClient.isEnabled());
    }

    @Test
    void getModelName_afterReload_returnsModel() {
        when(settingRepository.findById("ai.model")).thenReturn(Optional.of(setting("ai.model", "mimo-v2.5-pro")));
        aiClient.reloadConfig();
        assertEquals("mimo-v2.5-pro", aiClient.getModelName());
    }

    @Test
    void reloadConfig_handlesMissingSettings_usesDefaults() {
        when(settingRepository.findById("ai.enabled")).thenReturn(Optional.empty());
        when(settingRepository.findById("ai.base-url")).thenReturn(Optional.empty());
        when(settingRepository.findById("ai.api-key")).thenReturn(Optional.empty());
        when(settingRepository.findById("ai.model")).thenReturn(Optional.empty());
        when(settingRepository.findById("ai.max-tokens")).thenReturn(Optional.empty());
        when(settingRepository.findById("ai.temperature")).thenReturn(Optional.empty());

        aiClient.reloadConfig();
        // Default for enabled is "true" in getSetting
        assertTrue(aiClient.isEnabled());
    }

    @Test
    void reloadConfig_withExplicitFalse_disablesAI() {
        when(settingRepository.findById("ai.enabled")).thenReturn(Optional.of(setting("ai.enabled", "false")));
        when(settingRepository.findById("ai.base-url")).thenReturn(Optional.of(setting("ai.base-url", "https://api.test.com/v1")));
        when(settingRepository.findById("ai.api-key")).thenReturn(Optional.of(setting("ai.api-key", "key")));
        when(settingRepository.findById("ai.model")).thenReturn(Optional.of(setting("ai.model", "model")));
        when(settingRepository.findById("ai.max-tokens")).thenReturn(Optional.of(setting("ai.max-tokens", "4096")));
        when(settingRepository.findById("ai.temperature")).thenReturn(Optional.of(setting("ai.temperature", "0.7")));

        aiClient.reloadConfig();
        assertFalse(aiClient.isEnabled());
    }
}