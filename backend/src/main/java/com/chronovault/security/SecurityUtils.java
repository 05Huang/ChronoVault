package com.chronovault.security;

import com.chronovault.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;

/**
 * Utility class for safe authentication operations.
 * Prevents NPE when Authentication object or principal name is missing.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // utility class
    }

    /**
     * Safely extract the username from the Authentication object.
     *
     * @param auth the Spring Security Authentication object (may be null)
     * @return the non-blank username
     * @throws UnauthorizedException if auth is null, or the principal name is null/blank
     */
    public static String getCurrentUsername(Authentication auth) {
        if (auth == null) {
            throw new UnauthorizedException("认证信息缺失：未提供身份凭证");
        }
        String name = auth.getName();
        if (name == null || name.isBlank()) {
            throw new UnauthorizedException("认证信息缺失：无法获取用户身份");
        }
        return name;
    }
}
