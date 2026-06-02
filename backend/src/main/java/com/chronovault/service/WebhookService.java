package com.chronovault.service;

import com.chronovault.entity.WebhookDeliveryLog;
import com.chronovault.entity.WebhookEndpoint;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.WebhookDeliveryLogRepository;
import com.chronovault.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final int MAX_RETRIES = 3;

    @Transactional(readOnly = true)
    public List<WebhookEndpoint> getEndpoints() {
        return endpointRepository.findAll();
    }

    @Transactional
    public WebhookEndpoint createEndpoint(WebhookEndpoint endpoint) {
        return endpointRepository.save(endpoint);
    }

    @Transactional
    public WebhookEndpoint updateEndpoint(Long id, WebhookEndpoint updates) {
        WebhookEndpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook 不存在: " + id));
        if (updates.getUrl() != null) endpoint.setUrl(updates.getUrl());
        if (updates.getSecret() != null) endpoint.setSecret(updates.getSecret());
        if (updates.getEvents() != null) endpoint.setEvents(updates.getEvents());
        endpoint.setEnabled(updates.isEnabled());
        return endpointRepository.save(endpoint);
    }

    @Transactional
    public void deleteEndpoint(Long id) {
        endpointRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryLog> getDeliveryLogs(Long webhookId) {
        return deliveryLogRepository.findByWebhookIdOrderByCreatedAtDesc(webhookId);
    }

    /**
     * Deliver an event to all matching webhook endpoints.
     * Uses exponential backoff for retries.
     */
    @Async
    public void deliverEvent(String eventType, String payload) {
        List<WebhookEndpoint> endpoints = endpointRepository.findByEnabledTrue();

        for (WebhookEndpoint endpoint : endpoints) {
            if (!shouldDeliver(endpoint, eventType)) continue;

            deliverWithRetry(endpoint, eventType, payload, 1);
        }
    }

    private boolean shouldDeliver(WebhookEndpoint endpoint, String eventType) {
        if (endpoint.getEvents() == null || endpoint.getEvents().isBlank()) return true;
        String[] subscribedEvents = endpoint.getEvents().split(",");
        for (String event : subscribedEvents) {
            if (event.trim().equalsIgnoreCase(eventType)) return true;
        }
        return false;
    }

    private void deliverWithRetry(WebhookEndpoint endpoint, String eventType, String payload, int attempt) {
        try {
            String signature = computeHmac(endpoint.getSecret(), payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Event", eventType)
                    .header("X-Webhook-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            WebhookDeliveryLog logEntry = WebhookDeliveryLog.builder()
                    .webhook(endpoint)
                    .eventType(eventType)
                    .success(response.statusCode() >= 200 && response.statusCode() < 300)
                    .responseCode(response.statusCode())
                    .attempt(attempt)
                    .build();
            deliveryLogRepository.save(logEntry);

            if (!logEntry.isSuccess() && attempt < MAX_RETRIES) {
                long delay = (long) Math.pow(2, attempt) * 1000;
                Thread.sleep(delay);
                deliverWithRetry(endpoint, eventType, payload, attempt + 1);
            }
        } catch (Exception e) {
            log.error("Webhook delivery failed for {}: {}", endpoint.getUrl(), e.getMessage());
            WebhookDeliveryLog logEntry = WebhookDeliveryLog.builder()
                    .webhook(endpoint)
                    .eventType(eventType)
                    .success(false)
                    .error(e.getMessage())
                    .attempt(attempt)
                    .build();
            deliveryLogRepository.save(logEntry);

            if (attempt < MAX_RETRIES) {
                try {
                    long delay = (long) Math.pow(2, attempt) * 1000;
                    Thread.sleep(delay);
                    deliverWithRetry(endpoint, eventType, payload, attempt + 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private String computeHmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("Failed to compute HMAC: {}", e.getMessage());
            return "";
        }
    }
}