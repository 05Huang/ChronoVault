package com.chronovault.storage;

import com.chronovault.cache.CacheService;
import com.chronovault.cache.CacheKeyBuilder;
import com.chronovault.entity.StorageTarget;
import com.chronovault.repository.StorageTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageHealthChecker {

    private final StorageTargetRepository storageTargetRepository;
    private final StorageRouter storageRouter;
    private final CacheService cacheService;

    @Scheduled(fixedRate = 300000) // every 5 minutes
    public void checkAllStorages() {
        List<StorageTarget> targets = storageTargetRepository.findAll();
        for (StorageTarget target : targets) {
            try {
                StorageProvider.StorageHealthInfo health = storageRouter.getHealth(target);
                cacheService.put(CacheKeyBuilder.storageHealth(target.getId()), health, CacheKeyBuilder.STORAGE_HEALTH_TTL);

                // Also update used/total bytes in DB
                try {
                    long usedBytes = storageRouter.getUsedBytes(target);
                    target.setUsedBytes(usedBytes);
                    long totalBytes = storageRouter.getTotalBytes(target);
                    if (totalBytes > 0) {
                        target.setTotalBytes(totalBytes);
                    }
                    storageTargetRepository.save(target);
                } catch (Exception e) {
                    log.debug("Failed to update storage metrics for {}: {}", target.getName(), e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Health check failed for storage {}: {}", target.getName(), e.getMessage());
                cacheService.put(CacheKeyBuilder.storageHealth(target.getId()),
                        new StorageProvider.StorageHealthInfo("ERROR", "0", "0ms", "0 MB/s", 1), CacheKeyBuilder.STORAGE_HEALTH_TTL);
            }
        }
    }

    public StorageProvider.StorageHealthInfo getHealth(Long storageId) {
        StorageProvider.StorageHealthInfo cached = cacheService.get(CacheKeyBuilder.storageHealth(storageId),
                StorageProvider.StorageHealthInfo.class);
        if (cached != null) return cached;

        StorageTarget target = storageTargetRepository.findById(storageId).orElse(null);
        if (target == null) {
            return new StorageProvider.StorageHealthInfo("UNKNOWN", "0", "0ms", "0 MB/s", 0);
        }

        try {
            StorageProvider.StorageHealthInfo health = storageRouter.getHealth(target);
            cacheService.put(CacheKeyBuilder.storageHealth(storageId), health, CacheKeyBuilder.STORAGE_HEALTH_TTL);
            return health;
        } catch (Exception e) {
            return new StorageProvider.StorageHealthInfo("ERROR", "0", "0ms", "0 MB/s", 1);
        }
    }
}
