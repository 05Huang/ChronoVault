package com.chronovault.service;

import com.chronovault.ai.AiClient;
import com.chronovault.dto.settings.ApiKeyDTO;
import com.chronovault.dto.settings.AuditLogDTO;
import com.chronovault.dto.settings.CreateApiKeyResponse;
import com.chronovault.dto.settings.GenerateKeyRequest;
import com.chronovault.entity.ApiKey;
import com.chronovault.entity.AuditLog;
import com.chronovault.entity.SystemSetting;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ApiKeyRepository;
import com.chronovault.repository.AuditLogRepository;
import com.chronovault.repository.SystemSettingRepository;
import com.chronovault.security.SensitiveDataMasker;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final ApiKeyRepository apiKeyRepository;
    private final AuditLogRepository auditLogRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final UserService userService;
    private final AiClient aiClient;

    public List<ApiKeyDTO> getApiKeys(String email) {
        User user = userService.getByEmail(email);
        return apiKeyRepository.findByUserId(user.getId()).stream()
                .map(ApiKeyDTO::from)
                .toList();
    }

    @Transactional
    public CreateApiKeyResponse generateKey(String email, GenerateKeyRequest request) {
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
                .ipAddress(getClientIp())
                .build();
        auditLogRepository.save(log);

        return CreateApiKeyResponse.of(ApiKeyDTO.from(apiKey), rawKey);
    }

    @Transactional
    public void deleteKey(Long id) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API 密钥不存在: " + id));
        apiKeyRepository.delete(key);
    }

    public List<AuditLogDTO> getAuditLogs() {
        // Use paginated query (max 100) to prevent OOM
        return auditLogRepository.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, 100))
                .stream()
                .map(AuditLogDTO::from)
                .toList();
    }

    public Page<AuditLogDTO> searchAuditLogs(String action, Long userId, LocalDateTime since, LocalDateTime until, int page, int size) {
        return auditLogRepository.search(action, userId, since, until,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(AuditLogDTO::from);
    }

    /**
     * Export audit logs as a list of maps for CSV generation.
     * Supports optional time range filtering.
     * Limited to 10000 records to prevent OOM.
     */
    public List<Map<String, String>> exportAuditLogs(LocalDateTime since, LocalDateTime until) {
        Page<AuditLog> page;
        if (since != null || until != null) {
            LocalDateTime start = since != null ? since : LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime end = until != null ? until : LocalDateTime.now().plusDays(1);
            page = auditLogRepository.findByCreatedAtBetween(start, end,
                    PageRequest.of(0, 10000, Sort.by(Sort.Direction.DESC, "createdAt")));
        } else {
            page = auditLogRepository.findAllByOrderByCreatedAtDesc(
                    PageRequest.of(0, 10000));
        }

        return page.getContent().stream().map(log -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(log.getId()));
            row.put("user", log.getUser() != null ? log.getUser().getEmail() : "system");
            row.put("action", log.getAction() != null ? log.getAction() : "");
            row.put("changeType", log.getChangeType() != null ? log.getChangeType() : "");
            row.put("resourceType", log.getResourceType() != null ? log.getResourceType() : "");
            row.put("resourceId", log.getResourceId() != null ? String.valueOf(log.getResourceId()) : "");
            row.put("ipAddress", log.getIpAddress() != null ? log.getIpAddress() : "");
            row.put("userAgent", log.getUserAgent() != null ? log.getUserAgent() : "");
            row.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : "");
            return row;
        }).toList();
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

    public Map<String, Object> getAiConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", getSettingValue("ai.enabled", "true").equalsIgnoreCase("true"));
        config.put("baseUrl", getSettingValue("ai.base-url", "https://api.xiaomimimo.com/v1"));
        String apiKey = getSettingValue("ai.api-key", "");
        config.put("apiKey", SensitiveDataMasker.maskApiKey(apiKey));
        config.put("model", getSettingValue("ai.model", "mimo-v2.5-pro"));
        config.put("maxTokens", Integer.parseInt(getSettingValue("ai.max-tokens", "4096")));
        config.put("temperature", Double.parseDouble(getSettingValue("ai.temperature", "0.7")));
        return config;
    }

    @Transactional
    public void updateAiConfig(Map<String, Object> config) {
        if (config.containsKey("enabled")) {
            saveSetting("ai.enabled", String.valueOf(config.get("enabled")));
        }
        if (config.containsKey("baseUrl")) {
            saveSetting("ai.base-url", String.valueOf(config.get("baseUrl")));
        }
        if (config.containsKey("apiKey")) {
            String val = String.valueOf(config.get("apiKey"));
            // Only update if not masked
            if (!val.contains("*")) {
                saveSetting("ai.api-key", val);
            }
        }
        if (config.containsKey("model")) {
            saveSetting("ai.model", String.valueOf(config.get("model")));
        }
        if (config.containsKey("maxTokens")) {
            saveSetting("ai.max-tokens", String.valueOf(config.get("maxTokens")));
        }
        if (config.containsKey("temperature")) {
            saveSetting("ai.temperature", String.valueOf(config.get("temperature")));
        }
        aiClient.reloadConfig();
    }

    private String getSettingValue(String key, String defaultValue) {
        return systemSettingRepository.findById(key)
                .map(SystemSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultValue);
    }

    private void saveSetting(String key, String value) {
        SystemSetting setting = systemSettingRepository.findById(key).orElse(new SystemSetting());
        setting.setKey(key);
        setting.setValue(value);
        systemSettingRepository.save(setting);
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip != null && !ip.isBlank()) {
                    return ip.split(",")[0].trim();
                }
                ip = request.getHeader("X-Real-IP");
                if (ip != null && !ip.isBlank()) {
                    return ip;
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}
