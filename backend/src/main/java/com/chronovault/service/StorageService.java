package com.chronovault.service;

import com.chronovault.dto.storage.StorageDistributionDTO;
import com.chronovault.dto.storage.StorageHealthDTO;
import com.chronovault.dto.storage.StorageOverviewDTO;
import com.chronovault.entity.StorageTarget;
import com.chronovault.entity.User;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.storage.StorageHealthChecker;
import com.chronovault.storage.StorageProvider;
import com.chronovault.storage.StorageRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final StorageTargetRepository storageTargetRepository;
    private final StorageHealthChecker healthChecker;
    private final StorageRouter storageRouter;
    private final UserService userService;

    public List<StorageOverviewDTO> getOverview() {
        return storageTargetRepository.findAll().stream()
                .map(t -> {
                    double usagePercent = t.getTotalBytes() != null && t.getTotalBytes() > 0
                            ? (double) (t.getUsedBytes() != null ? t.getUsedBytes() : 0) / t.getTotalBytes() * 100 : 0;
                    return new StorageOverviewDTO(t.getType().name(), t.getName(),
                            t.getUsedBytes() != null ? t.getUsedBytes() : 0,
                            t.getTotalBytes() != null ? t.getTotalBytes() : 0,
                            Math.round(usagePercent * 10.0) / 10.0, t.getStatus().name());
                })
                .toList();
    }

    public List<StorageDistributionDTO> getDistribution() {
        Long sum = storageTargetRepository.sumUsedBytes();
        final long totalUsed = (sum == null || sum == 0) ? 1L : sum;

        return storageTargetRepository.findAll().stream()
                .map(t -> {
                    long used = t.getUsedBytes() != null ? t.getUsedBytes() : 0;
                    double percent = (double) used / totalUsed * 100;
                    return new StorageDistributionDTO(t.getName(), used,
                            Math.round(percent * 10.0) / 10.0, t.getType().name());
                })
                .toList();
    }

    public StorageHealthDTO getHealth() {
        List<StorageTarget> targets = storageTargetRepository.findAll();
        if (targets.isEmpty()) {
            return new StorageHealthDTO("无存储目标", "0", "0ms", "0 MB/s", 0);
        }

        // Aggregate health from all storage targets
        long totalErrors = 0;
        String overallStatus = "健康";
        for (StorageTarget target : targets) {
            StorageProvider.StorageHealthInfo health = healthChecker.getHealth(target.getId());
            totalErrors += health.errorCount();
            if ("ERROR".equals(health.status())) {
                overallStatus = "异常";
            }
        }

        return new StorageHealthDTO(overallStatus, "N/A", "N/A", "N/A", (int) totalErrors);
    }

    @Transactional
    public StorageOverviewDTO addTarget(String email, String type, String name, String endpoint, Long totalBytes) {
        User user = userService.getByEmail(email);
        StorageTarget target = StorageTarget.builder()
                .user(user)
                .type(StorageTarget.StorageType.valueOf(type))
                .name(name)
                .endpoint(endpoint)
                .usedBytes(0L)
                .totalBytes(totalBytes != null ? totalBytes : 1073741824L)
                .status(StorageTarget.StorageStatus.ACTIVE)
                .build();
        storageTargetRepository.save(target);

        // Initialize storage
        try {
            storageRouter.getProvider(target.getType()).initialize(target);
        } catch (Exception e) {
            target.setStatus(StorageTarget.StorageStatus.ERROR);
            storageTargetRepository.save(target);
        }

        return new StorageOverviewDTO(target.getType().name(), target.getName(),
                0L, target.getTotalBytes(), 0.0, target.getStatus().name());
    }
}
