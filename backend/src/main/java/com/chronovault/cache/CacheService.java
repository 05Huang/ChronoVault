package com.chronovault.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PREFIX = "cv:";

    public <T> void put(String key, T value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(PREFIX + key, json, ttl);
        } catch (JsonProcessingException e) {
            log.debug("Cache put failed for key {}: {}", key, e.getMessage(), e);
        } catch (Exception e) {
            log.debug("Cache put failed for key {}: {}", key, e.getMessage(), e);
        }
    }

    public <T> T get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(PREFIX + key);
            if (json == null) return null;
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.debug("Cache get failed for key {}: {}", key, e.getMessage(), e);
            return null;
        }
    }

    public <T> T get(String key, TypeReference<T> typeRef) {
        try {
            String json = redisTemplate.opsForValue().get(PREFIX + key);
            if (json == null) return null;
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.debug("Cache get failed for key {}: {}", key, e.getMessage(), e);
            return null;
        }
    }

    public void evict(String key) {
        redisTemplate.delete(PREFIX + key);
    }

    public boolean hasKey(String key) {
        Boolean exists = redisTemplate.hasKey(PREFIX + key);
        return Boolean.TRUE.equals(exists);
    }

    public void putString(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + key, value, ttl);
    }

    public String getString(String key) {
        return redisTemplate.opsForValue().get(PREFIX + key);
    }
}
