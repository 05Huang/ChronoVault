package com.chronovault.repository;

import com.chronovault.entity.WebhookDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, Long> {
    List<WebhookDeliveryLog> findByWebhookIdOrderByCreatedAtDesc(Long webhookId);
}