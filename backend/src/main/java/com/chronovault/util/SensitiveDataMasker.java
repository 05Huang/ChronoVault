package com.chronovault.util;

import java.util.regex.Pattern;

/**
 * Utility class for masking sensitive data in log messages.
 * Prevents accidental logging of passwords, keys, tokens, etc.
 */
public final class SensitiveDataMasker {

    private SensitiveDataMasker() {}

    private static final String MASK = "****";
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|key|credential|private.?key|api.?key)\\s*[=:]\\s*\\S+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Mask sensitive fields in a string for safe logging.
     * Replaces password=xxx with password=****
     */
    public static String maskSensitiveFields(String input) {
        if (input == null || input.isBlank()) return input;
        return PASSWORD_PATTERN.matcher(input).replaceAll(m -> {
            String fullMatch = m.group(0);
            int eqIdx = fullMatch.indexOf('=');
            if (eqIdx < 0) eqIdx = fullMatch.indexOf(':');
            if (eqIdx < 0) return fullMatch;
            return fullMatch.substring(0, eqIdx + 1) + " " + MASK;
        });
    }

    /**
     * Mask an email address for display: user@domain.com -> u***@***.com
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return email;
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) return MASK;
        int dotIdx = email.lastIndexOf('.');
        String local = email.substring(0, 1) + "***";
        String domain = dotIdx > atIdx ? "***" + email.substring(dotIdx) : "***";
        return local + "@" + domain;
    }

    /**
     * Mask a token/API key: first 4 chars + ****
     */
    public static String maskToken(String token) {
        if (token == null || token.length() <= 4) return MASK;
        return token.substring(0, 4) + MASK;
    }

    /**
     * Mask an IP address: 192.168.1.*** or similar
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return ip;
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) return MASK;
        return ip.substring(0, lastDot + 1) + "***";
    }
}