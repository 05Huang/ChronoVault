package com.chronovault.controller;

import com.chronovault.entity.WebhookDeliveryLog;
import com.chronovault.entity.WebhookEndpoint;
import com.chronovault.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private WebhookService webhookService;

    @InjectMocks
    private WebhookController controller;

    @Test
    void getEndpoints_returnsList() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .id(1L).url("https://example.com/hook").enabled(true).build();
        when(webhookService.getEndpoints()).thenReturn(List.of(endpoint));

        var response = controller.getEndpoints();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().data().size());
        assertEquals("https://example.com/hook", response.getBody().data().get(0).getUrl());
    }

    @Test
    void createEndpoint_validRequest_succeeds() {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .url("https://example.com/hook").enabled(true).build();
        when(webhookService.createEndpoint(any(WebhookEndpoint.class))).thenReturn(endpoint);

        var response = controller.createEndpoint(endpoint);
        assertEquals(201, response.getStatusCode().value());
        verify(webhookService).createEndpoint(endpoint);
    }

    @Test
    void deleteEndpoint_validId_succeeds() {
        doNothing().when(webhookService).deleteEndpoint(1L);

        var response = controller.deleteEndpoint(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(webhookService).deleteEndpoint(1L);
    }

    @Test
    void getDeliveryLogs_validId_returnsList() {
        when(webhookService.getDeliveryLogs(1L)).thenReturn(List.of());

        var response = controller.getDeliveryLogs(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void testWebhook_validId_succeeds() {
        doNothing().when(webhookService).deliverEvent(anyString(), anyString());

        var response = controller.testWebhook(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(webhookService).deliverEvent(eq("WEBHOOK_TEST"), anyString());
    }
}