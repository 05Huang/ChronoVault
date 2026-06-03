package com.chronovault.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-based distributed lock to prevent multiple instances from
 * executing the same scheduled task simultaneously.
 * Uses SET NX PX pattern for atomic lock acquisition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final StringRedisTemplate redisTemplate;

    /**
     * Try to acquire a distributed lock.
     * @param lockName unique lock identifier (e.g., "auto-snapshot", "health-check")
     * @param maxLockDuration maximum time to hold the lock
     * @return lock value (UUID) if acquired, null if already locked
     */
    public String tryLock(String lockName, Duration maxLockDuration) {
        String lockKey = "cv:lock:" + lockName;
        String lockValue = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, maxLockDuration);
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Distributed lock acquired: {}", lockName);
                return lockValue;
            }
            log.debug("Distributed lock already held: {}", lockName);
            return null;
        } catch (Exception e) {
            // Redis unavailable — allow execution as fallback (single instance mode)
            log.warn("Redis unavailable for distributed lock '{}', executing without lock: {}",
                    lockName, e.getMessage());
            return lockValue;
        }
    }

    /**
     * Release a distributed lock (only if we still own it).
     */
    public void releaseLock(String lockName, String lockValue) {
        if (lockValue == null) return;
        String lockKey = "cv:lock:" + lockName;
        try {
            // Use Lua script for atomic check-and-delete
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class),
                    java.util.List.of(lockKey),
                    lockValue);
            log.debug("Distributed lock released: {}", lockName);
        } catch (Exception e) {
            log.warn("Failed to release distributed lock '{}': {}", lockName, e.getMessage());
        }
    }
}