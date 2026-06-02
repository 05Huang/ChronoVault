package com.chronovault.repository;

import com.chronovault.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
    List<WebhookEndpoint> findByEnabledTrue();
}