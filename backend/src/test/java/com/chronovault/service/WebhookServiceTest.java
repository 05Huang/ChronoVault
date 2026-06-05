package com.chronovault.service;

import com.chronovault.entity.WebhookEndpoint;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.WebhookDeliveryLogRepository;
import com.chronovault.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock private WebhookEndpointRepository endpointRepository;
    @Mock private WebhookDeliveryLogRepository deliveryLogRepository;

    @InjectMocks
    private WebhookService service;

    @Test
    void getEndpoints_returnsAll() {
        WebhookEndpoint ep = WebhookEndpoint.builder().id(1L).url("https://example.com/hook").enabled(true).build();
        when(endpointRepository.findAll()).thenReturn(List.of(ep));
        var result = service.getEndpoints();
        assertEquals(1, result.size());
    }

    @Test
    void createEndpoint_savesAndReturns() {
        WebhookEndpoint ep = WebhookEndpoint.builder().url("https://example.com/hook").enabled(true).build();
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(inv -> {
            WebhookEndpoint e = inv.getArgument(0);
            var field = WebhookEndpoint.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(e, 1L);
            return e;
        });
        var result = service.createEndpoint(ep);
        assertNotNull(result);
        verify(endpointRepository).save(any(WebhookEndpoint.class));
    }

    @Test
    void updateEndpoint_nonExisting_throwsException() {
        when(endpointRepository.findById(999L)).thenReturn(Optional.empty());
        WebhookEndpoint updates = WebhookEndpoint.builder().url("https://new.com").build();
        assertThrows(ResourceNotFoundException.class, () -> service.updateEndpoint(999L, updates));
    }

    @Test
    void updateEndpoint_existing_updatesFields() {
        WebhookEndpoint existing = WebhookEndpoint.builder().id(1L).url("https://old.com").enabled(true).build();
        when(endpointRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(inv -> inv.getArgument(0));
        WebhookEndpoint updates = WebhookEndpoint.builder().url("https://new.com").enabled(false).build();

        var result = service.updateEndpoint(1L, updates);
        assertEquals("https://new.com", result.getUrl());
        assertFalse(result.isEnabled());
    }

    @Test
    void deleteEndpoint_existing_deletes() {
        service.deleteEndpoint(1L);
        verify(endpointRepository).deleteById(1L);
    }

    @Test
    void getDeliveryLogs_returnsLogs() {
        when(deliveryLogRepository.findByWebhookIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        var result = service.getDeliveryLogs(1L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void deliverEvent_noEndpoints_doesNothing() {
        when(endpointRepository.findByEnabledTrue()).thenReturn(List.of());
        service.deliverEvent("snapshot.created", "{}");
        verify(deliveryLogRepository, never()).save(any());
    }

    @Test
    void deliverEvent_withEnabledEndpoint_logsDelivery() {
        WebhookEndpoint ep = WebhookEndpoint.builder().id(1L).url("https://example.com/hook")
                .enabled(true).events("snapshot.created").build();
        when(endpointRepository.findByEnabledTrue()).thenReturn(List.of(ep));
        // The HTTP call will fail (no real server), but delivery log should still be saved
        service.deliverEvent("snapshot.created", "{\"test\":true}");
        // Verify delivery was attempted (log entry saved even on failure)
        verify(deliveryLogRepository, atLeastOnce()).save(any());
    }

    @Test
    void deliverEvent_withAllEventsFilter_deliversAll() {
        WebhookEndpoint ep = WebhookEndpoint.builder().id(1L).url("https://example.com/hook")
                .enabled(true).events("").build(); // empty = all events
        when(endpointRepository.findByEnabledTrue()).thenReturn(List.of(ep));
        service.deliverEvent("alert.created", "{}");
        verify(deliveryLogRepository, atLeastOnce()).save(any());
    }

    @Test
    void deliverEvent_withSpecificEventFilter_onlyDeliversMatching() {
        WebhookEndpoint ep = WebhookEndpoint.builder().id(1L).url("https://example.com/hook")
                .enabled(true).events("snapshot.created,snapshot.deleted").build();
        when(endpointRepository.findByEnabledTrue()).thenReturn(List.of(ep));

        // This event type is NOT subscribed
        service.deliverEvent("alert.created", "{}");
        verify(deliveryLogRepository, never()).save(any());
    }

    @Test
    void deliverEvent_disabledEndpoint_skipped() {
        WebhookEndpoint ep = WebhookEndpoint.builder().id(1L).url("https://example.com/hook")
                .enabled(false).build();
        when(endpointRepository.findByEnabledTrue()).thenReturn(List.of());
        service.deliverEvent("snapshot.created", "{}");
        verify(deliveryLogRepository, never()).save(any());
    }

    @Test
    void updateEndpoint_updatesSecretAndEvents() {
        WebhookEndpoint existing = WebhookEndpoint.builder().id(1L).url("https://old.com")
                .secret("old-secret").events("old-event").enabled(true).build();
        when(endpointRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(inv -> inv.getArgument(0));

        WebhookEndpoint updates = WebhookEndpoint.builder()
                .secret("new-secret").events("new-event").enabled(true).build();

        var result = service.updateEndpoint(1L, updates);
        assertEquals("new-secret", result.getSecret());
        assertEquals("new-event", result.getEvents());
    }
}