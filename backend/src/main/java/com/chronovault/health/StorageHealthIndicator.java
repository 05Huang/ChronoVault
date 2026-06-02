package com.chronovault.health;

import com.chronovault.entity.StorageTarget;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.storage.StorageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageHealthIndicator implements HealthIndicator {

    private final StorageTargetRepository storageTargetRepository;
    private final StorageRouter storageRouter;

    @Override
    public Health health() {
        try {
            List<StorageTarget> targets = storageTargetRepository.findAll();
            int healthy = 0;
            int unhealthy = 0;

            for (StorageTarget target : targets) {
                try {
                    storageRouter.getUsedBytes(target);
                    healthy++;
                } catch (Exception e) {
                    unhealthy++;
                }
            }

            return Health.up()
                    .withDetail("totalTargets", targets.size())
                    .withDetail("healthy", healthy)
                    .withDetail("unhealthy", unhealthy)
                    .build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}