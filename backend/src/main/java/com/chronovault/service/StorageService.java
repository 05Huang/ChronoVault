package com.chronovault.service;

import com.chronovault.dto.storage.StorageDistributionDTO;
import com.chronovault.dto.storage.StorageHealthDTO;
import com.chronovault.dto.storage.StorageOverviewDTO;
import com.chronovault.entity.StorageTarget;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.security.CredentialEncryptor;
import com.chronovault.storage.StorageHealthChecker;
import com.chronovault.storage.StorageProvider;
import com.chronovault.storage.StorageRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final StorageTargetRepository storageTargetRepository;
    private final StorageHealthChecker healthChecker;
    private final StorageRouter storageRouter;
    private final UserService userService;
    private final CredentialEncryptor encryptor;
    private final ObjectMapper objectMapper;

    public List<StorageOverviewDTO> getOverview() {
        return storageTargetRepository.findAll().stream()
                .map(t -> {
                    double usagePercent = t.getTotalBytes() != null && t.getTotalBytes() > 0
                            ? (double) (t.getUsedBytes() != null ? t.getUsedBytes() : 0) / t.getTotalBytes() * 100 : 0;
                    return new StorageOverviewDTO(t.getId(), t.getType().name(), t.getName(),
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
            return new StorageHealthDTO("暂无存储目标", "-", "-", "-", 0);
        }

        long totalErrors = 0;
        String overallStatus = "健康";
        long totalLatencyMs = 0;
        int measuredCount = 0;

        for (StorageTarget target : targets) {
            StorageProvider.StorageHealthInfo health = healthChecker.getHealth(target.getId());
            totalErrors += health.errorCount();
            if ("ERROR".equals(health.status())) {
                overallStatus = "异常";
            }
            // Parse latency from health info (format: "XXms")
            try {
                String lat = health.latency();
                if (lat != null && lat.endsWith("ms")) {
                    totalLatencyMs += Long.parseLong(lat.replace("ms", "").trim());
                    measuredCount++;
                }
            } catch (Exception ignored) {}
        }

        String avgLatency = measuredCount > 0 ? (totalLatencyMs / measuredCount) + "ms" : "检测中...";
        return new StorageHealthDTO(overallStatus, targets.size() + " 个目标", avgLatency, "-", (int) totalErrors);
    }

    @Transactional
    public StorageOverviewDTO addTarget(String email, String type, String name, String endpoint,
                                        Long totalBytes, String accessKey, String secretKey,
                                        String region, String bucket) {
        User user = userService.getByEmail(email);
        StorageTarget.StorageType storageType = StorageTarget.StorageType.valueOf(type);

        StorageTarget target = StorageTarget.builder()
                .user(user)
                .type(storageType)
                .name(name)
                .endpoint(endpoint)
                .usedBytes(0L)
                .totalBytes(totalBytes != null ? totalBytes : 0L)
                .status(StorageTarget.StorageStatus.ACTIVE)
                .build();

        // Encrypt and store credentials for S3/OSS
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            try {
                Map<String, String> creds = new HashMap<>();
                creds.put("accessKey", accessKey);
                creds.put("secretKey", secretKey);
                String credsJson = objectMapper.writeValueAsString(creds);
                target.setCredentialsEncrypted(encryptor.encrypt(credsJson));
                log.info("Credentials encrypted for storage target {}", name);
            } catch (Exception e) {
                log.error("Failed to encrypt credentials: {}", e.getMessage());
                throw new RuntimeException("凭证加密失败: " + e.getMessage());
            }
        }

        // Store config (region, bucket, etc.)
        try {
            Map<String, Object> config = new HashMap<>();
            if (region != null && !region.isBlank()) config.put("region", region);
            if (bucket != null && !bucket.isBlank()) config.put("bucket", bucket);
            if (!config.isEmpty()) {
                target.setConfig(objectMapper.writeValueAsString(config));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize config: {}", e.getMessage());
        }

        storageTargetRepository.save(target);

        // Initialize storage (verify connectivity)
        try {
            storageRouter.getProvider(storageType).initialize(target);
            log.info("Storage target {} initialized successfully", name);
        } catch (Exception e) {
            log.warn("Storage initialization failed for {}: {}", name, e.getMessage());
            target.setStatus(StorageTarget.StorageStatus.ERROR);
            storageTargetRepository.save(target);
        }

        return new StorageOverviewDTO(target.getId(), target.getType().name(), target.getName(),
                0L, target.getTotalBytes(), 0.0, target.getStatus().name());
    }

    @Transactional
    public void deleteTarget(Long id) {
        StorageTarget target = storageTargetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("存储目标不存在: " + id));
        storageTargetRepository.delete(target);
    }
}
