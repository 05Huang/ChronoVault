package com.chronovault.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Adds security response headers to all HTTP responses.
 */
@Component
@Order(1)
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Prevent MIME type sniffing
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking
        httpResponse.setHeader("X-Frame-Options", "DENY");

        // XSS protection
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

        // Referrer policy
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions policy
        httpResponse.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        // Content Security Policy (basic)
        httpResponse.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; " +
                "connect-src 'self' ws: wss:; frame-ancestors 'none'");

        // HSTS for HTTPS
        jakarta.servlet.http.HttpServletRequest httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
        if (httpRequest.isSecure()) {
            httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        chain.doFilter(request, response);
    }
}