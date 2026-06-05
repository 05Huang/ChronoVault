package com.chronovault.controller;

import com.chronovault.dto.alert.*;
import com.chronovault.service.AlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    @Mock
    private AlertService alertService;

    @InjectMocks
    private AlertController controller;

    private AlertDTO createTestAlert(Long id, String title) {
        return new AlertDTO(id, "WARNING", title, "test description", "server", "category",
                null, null, null, "OPEN", "2026-06-01 10:00", false);
    }

    @Test
    void getAlerts_defaultPagination_returnsPage() {
        var page = new PageImpl<>(
                List.of(createTestAlert(1L, "Test Alert")),
                PageRequest.of(0, 20), 1);
        when(alertService.getAlertsPaged(null, 0, 20)).thenReturn(page);

        var response = controller.getAlerts(null, 0, 20);
        assertNotNull(response);
    }

    @Test
    void getAlerts_withPagination_returnsPage() {
        var page = new PageImpl<>(
                List.of(createTestAlert(1L, "Alert 1")),
                PageRequest.of(0, 10), 1);
        when(alertService.getAlertsPaged(null, 0, 10)).thenReturn(page);

        var response = controller.getAlerts(null, 0, 10);
        assertNotNull(response);
    }

    @Test
    void getStats_returnsStats() {
        AlertStatsDTO stats = new AlertStatsDTO(5, 2, 0, 1, 3, 2);
        when(alertService.getStats()).thenReturn(stats);

        var response = controller.getStats();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(5, response.getBody().data().total());
    }

    @Test
    void dismiss_validId_succeeds() {
        doNothing().when(alertService).dismiss(1L);

        var response = controller.dismiss(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(alertService).dismiss(1L);
    }

    @Test
    void restart_validId_succeeds() {
        doNothing().when(alertService).restartContainer(1L);

        var response = controller.restart(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(alertService).restartContainer(1L);
    }

    @Test
    void deleteRule_validId_succeeds() {
        doNothing().when(alertService).deleteRule(1L);

        var response = controller.deleteRule(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(alertService).deleteRule(1L);
    }

    // ===== expandStorage test =====

    @Test
    void expandStorage_validId_succeeds() {
        doNothing().when(alertService).expandStorage(1L);
        var response = controller.expandStorage(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    // ===== rollbackConfig test =====

    @Test
    void rollbackConfig_validId_succeeds() {
        doNothing().when(alertService).rollbackConfig(1L);
        var response = controller.rollbackConfig(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    // ===== getRules test =====

    @Test
    void getRules_returnsList() {
        when(alertService.getRules("test@test.com")).thenReturn(List.of());
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.getRules(auth);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().data());
    }

    // ===== createRule test =====

    @Test
    void createRule_validRequest_returnsCreated() {
        AlertRuleDTO rule = new AlertRuleDTO(1L, "Disk High", "disk", 90.0, 5, "WARNING", true);
        when(alertService.createRule(eq("test@test.com"), any())).thenReturn(rule);

        CreateAlertRuleRequest request = new CreateAlertRuleRequest("Disk High", "disk", 90, 5, "WARNING", true);
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.createRule(auth, request);
        assertEquals(201, response.getStatusCode().value());
    }

    // ===== getIntegrations test =====

    @Test
    void getIntegrations_returnsList() {
        when(alertService.getIntegrations("test@test.com")).thenReturn(List.of());
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.getIntegrations(auth);
        assertEquals(200, response.getStatusCode().value());
    }

    // ===== deleteIntegration test =====

    @Test
    void deleteIntegration_validId_succeeds() {
        doNothing().when(alertService).deleteIntegration(1L);
        var response = controller.deleteIntegration(1L);
        assertEquals(200, response.getStatusCode().value());
    }
}