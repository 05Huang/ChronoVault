package com.chronovault.controller;

import com.chronovault.dto.integration.IntegrationDTO;
import com.chronovault.dto.integration.CreateIntegrationRequest;
import com.chronovault.dto.integration.UpdateIntegrationRequest;
import com.chronovault.service.AlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegrationControllerTest {

    @Mock private AlertService alertService;

    @InjectMocks
    private IntegrationController controller;

    @Test
    void getIntegrations_returnsList() {
        when(alertService.getIntegrations("test@test.com")).thenReturn(List.of());
        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.getIntegrations(auth);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void createIntegration_succeeds() {
        IntegrationDTO integration = new IntegrationDTO(1L, "SLACK", "Slack", "https://hooks.slack.com/test", true);
        when(alertService.createIntegration(eq("test@test.com"), anyString(), anyString(), anyString())).thenReturn(integration);
        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        CreateIntegrationRequest request = new CreateIntegrationRequest("SLACK", "Slack", "https://hooks.slack.com/test");
        var response = controller.createIntegration(auth, request);
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void updateIntegration_succeeds() {
        IntegrationDTO integration = new IntegrationDTO(1L, "SLACK", "Slack", "https://hooks.slack.com/test", false);
        when(alertService.updateIntegration(1L, false)).thenReturn(integration);
        UpdateIntegrationRequest body = new UpdateIntegrationRequest(false);
        var response = controller.updateIntegration(1L, body);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getIntegrations_withDifferentUser_returnsList() {
        when(alertService.getIntegrations("other@test.com")).thenReturn(List.of());
        var auth = new UsernamePasswordAuthenticationToken("other@test.com", null, List.of());
        var response = controller.getIntegrations(auth);
        assertEquals(200, response.getStatusCode().value());
    }
}