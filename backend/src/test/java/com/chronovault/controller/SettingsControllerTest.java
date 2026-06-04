package com.chronovault.controller;

import com.chronovault.dto.settings.*;
import com.chronovault.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsControllerTest {

    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private SettingsController controller;

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("test@test.com", null);
    }

    @Test
    void getApiKeys_returnsList() {
        when(settingsService.getApiKeys("test@test.com")).thenReturn(List.of(
                new ApiKeyDTO(1L, "Test Key", "cv_abc", "FULL", "", "")));

        var response = controller.getApiKeys(auth());
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().data().size());
        assertEquals("Test Key", response.getBody().data().get(0).name());
    }

    @Test
    void generateKey_validRequest_succeeds() {
        GenerateKeyRequest request = new GenerateKeyRequest("New Key", "FULL");
        ApiKeyDTO dto = new ApiKeyDTO(1L, "New Key", "cv_xyz", "FULL", "", "");
        when(settingsService.generateKey("test@test.com", request))
                .thenReturn(CreateApiKeyResponse.of(dto, "cv_xyz_secret_key"));

        var response = controller.generateKey(auth(), request);
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().data().key());
    }

    @Test
    void deleteKey_validId_succeeds() {
        doNothing().when(settingsService).deleteKey(1L);

        var response = controller.deleteKey(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(settingsService).deleteKey(1L);
    }

    @Test
    void getAuditLogs_returnsList() {
        when(settingsService.getAuditLogs()).thenReturn(List.of());

        var response = controller.getAuditLogs();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getAiConfig_returnsConfig() {
        when(settingsService.getAiConfig()).thenReturn(Map.of("enabled", false));

        var response = controller.getAiConfig();
        assertEquals(200, response.getStatusCode().value());
        assertFalse((Boolean) response.getBody().data().get("enabled"));
    }

    @Test
    void updateAiConfig_validRequest_succeeds() {
        UpdateAiConfigRequest request = new UpdateAiConfigRequest(Map.of("enabled", true));

        var response = controller.updateAiConfig(request);
        assertEquals(200, response.getStatusCode().value());
        verify(settingsService).updateAiConfig(Map.of("enabled", true));
    }
}