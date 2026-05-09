package com.chronovault.service;

import com.chronovault.dto.settings.ApiKeyDTO;
import com.chronovault.dto.settings.AuditLogDTO;
import com.chronovault.dto.settings.GenerateKeyRequest;
import com.chronovault.entity.ApiKey;
import com.chronovault.entity.AuditLog;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ApiKeyRepository;
import com.chronovault.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final ApiKeyRepository apiKeyRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserService userService;

    public List<ApiKeyDTO> getApiKeys(String email) {
        User user = userService.getByEmail(email);
        return apiKeyRepository.findByUserId(user.getId()).stream()
                .map(ApiKeyDTO::from)
                .toList();
    }

    @Transactional
    public ApiKeyDTO generateKey(String email, GenerateKeyRequest request) {
        User user = userService.getByEmail(email);
        String rawKey = "cv_" + UUID.randomUUID().toString().replace("-", "");
        String prefix = rawKey.substring(0, 12) + "...";
        String keyHash = hashKey(rawKey);

        ApiKey.KeyScope scope = ApiKey.KeyScope.READ;
        if (request.scope() != null) {
            scope = ApiKey.KeyScope.valueOf(request.scope());
        }

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name(request.name())
                .prefix(prefix)
                .keyHash(keyHash)
                .scope(scope)
                .build();
        apiKeyRepository.save(apiKey);

        // Create audit log
        AuditLog log = AuditLog.builder()
                .user(user)
                .action("生成 API 密钥: " + request.name())
                .icon("key")
                .ipAddress("127.0.0.1")
                .build();
        auditLogRepository.save(log);

        return ApiKeyDTO.from(apiKey);
    }

    @Transactional
    public void deleteKey(Long id) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API 密钥不存在: " + id));
        apiKeyRepository.delete(key);
    }

    public List<AuditLogDTO> getAuditLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AuditLogDTO::from)
                .toList();
    }

    private String hashKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(key.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            return key;
        }
    }
}
