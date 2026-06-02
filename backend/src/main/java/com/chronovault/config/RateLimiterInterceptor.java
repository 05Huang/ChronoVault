package com.chronovault.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RateLimiterInterceptor implements HandlerInterceptor {

    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, RequestCounter> counters = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.equals("/health") || path.equals("/ws")) {
            return true;
        }

        String key = getClientKey(request);
        RequestCounter counter = counters.computeIfAbsent(key, k -> new RequestCounter());

        long now = System.currentTimeMillis();
        if (now - counter.windowStart > WINDOW_MS) {
            counter.windowStart = now;
            counter.count.set(0);
        }

        int currentCount = counter.count.incrementAndGet();
        if (currentCount > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\",\"data\":null}");
            log.warn("Rate limit exceeded for key: {}", key);
            return false;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, MAX_REQUESTS_PER_MINUTE - currentCount)));
        response.setHeader("X-RateLimit-Reset", String.valueOf((counter.windowStart + WINDOW_MS) / 1000));

        return true;
    }

    private String getClientKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "apikey:" + apiKey.substring(0, Math.min(8, apiKey.length()));
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return "ip:" + ip;
    }

    private static class RequestCounter {
        volatile long windowStart = System.currentTimeMillis();
        final AtomicInteger count = new AtomicInteger(0);
    }
}