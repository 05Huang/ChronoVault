package com.chronovault.service;

import com.chronovault.ai.AiClient;
import com.chronovault.dto.settings.ApiKeyDTO;
import com.chronovault.dto.settings.CreateApiKeyResponse;
import com.chronovault.dto.settings.GenerateKeyRequest;
import com.chronovault.entity.ApiKey;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ApiKeyRepository;
import com.chronovault.repository.AuditLogRepository;
import com.chronovault.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SystemSettingRepository systemSettingRepository;
    @Mock private UserService userService;
    @Mock private AiClient aiClient;

    @InjectMocks
    private SettingsService settingsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Test User").email("test@example.com").build();
    }

    @Test
    void getApiKeys_returnsUserKeys() {
        ApiKey key = ApiKey.builder().id(1L).user(testUser).name("Test Key").prefix("cv_123456...").scope(ApiKey.KeyScope.READ).build();
        when(userService.getByEmail("test@example.com")).thenReturn(testUser);
        when(apiKeyRepository.findByUserId(1L)).thenReturn(List.of(key));

        var result = settingsService.getApiKeys("test@example.com");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Key", result.get(0).name());
    }

    @Test
    void deleteKey_existingKey_deletes() {
        ApiKey key = ApiKey.builder().id(1L).user(testUser).name("Test Key").build();
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(key));

        settingsService.deleteKey(1L);

        verify(apiKeyRepository).delete(key);
    }

    @Test
    void deleteKey_nonExistingKey_throwsException() {
        when(apiKeyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> settingsService.deleteKey(999L));
    }

    @Test
    void getAiConfig_returnsConfig() {
        when(systemSettingRepository.findById("ai.enabled")).thenReturn(Optional.empty());
        when(systemSettingRepository.findById("ai.base-url")).thenReturn(Optional.empty());
        when(systemSettingRepository.findById("ai.api-key")).thenReturn(Optional.empty());
        when(systemSettingRepository.findById("ai.model")).thenReturn(Optional.empty());
        when(systemSettingRepository.findById("ai.max-tokens")).thenReturn(Optional.empty());
        when(systemSettingRepository.findById("ai.temperature")).thenReturn(Optional.empty());

        var result = settingsService.getAiConfig();

        assertNotNull(result);
        assertTrue((Boolean) result.get("enabled"));
        assertEquals("mimo-v2.5-pro", result.get("model"));
    }

    @Test
    void updateAiConfig_savesSettings() {
        when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());

        var config = new java.util.HashMap<String, Object>();
        config.put("enabled", false);
        config.put("model", "gpt-4");

        settingsService.updateAiConfig(config);

        verify(aiClient).reloadConfig();
    }

    @Test
    void getAuditLogs_returnsLogs() {
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = settingsService.getAuditLogs();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
