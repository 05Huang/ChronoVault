package com.chronovault.websocket;

import com.chronovault.security.JwtTokenProvider;
import com.chronovault.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final WebSocketConnectionTracker connectionTracker;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null || !tokenProvider.validateToken(token)) {
            log.warn("WebSocket handshake rejected: invalid or missing token from {}", request.getRemoteAddress());
            return false;
        }

        try {
            String email = tokenProvider.getEmailFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            attributes.put("user", userDetails);
            attributes.put("email", email);
            String sessionId = request.getURI().getQuery();
            if (sessionId != null) {
                connectionTracker.trackConnection(sessionId, email);
            }
            log.debug("WebSocket handshake authenticated for {}", email);
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(ServerHttpRequest request) {
        // Try Authorization header first
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String bearer = authHeaders.get(0);
            if (bearer.startsWith("Bearer ")) {
                return bearer.substring(7);
            }
        }
        // Fall back to query parameter (for SockJS)
        var uri = request.getURI();
        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String tokenParam = params.getFirst("token");
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam;
        }
        return null;
    }
}
