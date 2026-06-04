package com.chronovault.security;

import com.chronovault.entity.ApiKey;
import com.chronovault.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key authentication filter with Redis caching.
 * Caches validated API key lookups for 5 minutes to avoid hitting the database on every request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String CACHE_PREFIX = "cv:apikey:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // Cache entry: email|roleName
    private static final String NEGATIVE_CACHE = "NONE";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && token.startsWith("cv_")) {
            String keyHash = hashKey(token);
            String cacheKey = CACHE_PREFIX + keyHash;

            // Check Redis cache first
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (NEGATIVE_CACHE.equals(cached)) {
                // Known invalid key — skip DB query
                filterChain.doFilter(request, response);
                return;
            }

            if (cached != null && cached.contains("|")) {
                // Cache hit: parse email|role
                String[] parts = cached.split("\\|", 2);
                setAuthentication(parts[0], parts[1], request);
                filterChain.doFilter(request, response);
                return;
            }

            // Cache miss: query database
            apiKeyRepository.findByKeyHash(keyHash).ifPresent(apiKey -> {
                // Update last used time (async would be better, but keeping it simple)
                apiKey.setLastUsedAt(LocalDateTime.now());
                apiKeyRepository.save(apiKey);

                // Cache the result
                var user = apiKey.getUser();
                String cacheValue = user.getEmail() + "|" + user.getRole().name();
                redisTemplate.opsForValue().set(cacheKey, cacheValue, CACHE_TTL);

                // Set authentication
                setAuthentication(user.getEmail(), user.getRole().name(), request);
            });

            // If not found in DB, cache negative result to avoid repeated DB hits
            if (cached == null) {
                redisTemplate.opsForValue().set(cacheKey, NEGATIVE_CACHE, CACHE_TTL);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(String email, String roleName, HttpServletRequest request) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
        var authentication = new UsernamePasswordAuthenticationToken(
                email, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
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
