package com.chronovault.security;

import java.util.regex.Pattern;

/**
 * Utility class for masking sensitive data before logging.
 * Prevents accidental exposure of passwords, keys, tokens, and other credentials in log output.
 */
public final class SensitiveDataMasker {

    private static final String MASK = "****";
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|key|credential|private.?key|api.?key)\\s*[=:]\\s*\\S+");

    private SensitiveDataMasker() {
        // utility class
    }

    /**
     * Mask an API key: show first 4 and last 4 chars, mask the rest.
     * Example: "cv_a1b2c3d4e5f6" → "cv_a****e5f6"
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return "";
        if (apiKey.length() <= 8) return MASK;
        return apiKey.substring(0, 4) + MASK + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * Mask a password: always return "****".
     */
    public static String maskPassword(String password) {
        return MASK;
    }

    /**
     * Mask a token (JWT, bearer, etc.): show first 6 and last 4 chars.
     * Example: "eyJhbGciOiJI..." → "eyJhb****abcd"
     */
    public static String maskToken(String token) {
        if (token == null || token.isBlank()) return "";
        if (token.length() <= 10) return MASK;
        return token.substring(0, 6) + MASK + token.substring(token.length() - 4);
    }

    /**
     * Mask an SSH private key content: show key type prefix and length only.
     * Example: "-----BEGIN OPENSSH PRIVATE KEY-----\n..." → "SSH_KEY(type=OPENSSH, length=2048)"
     */
    public static String maskSshKey(String keyContent) {
        if (keyContent == null || keyContent.isBlank()) return "SSH_KEY(empty)";
        String type = "UNKNOWN";
        if (keyContent.contains("OPENSSH")) type = "OPENSSH";
        else if (keyContent.contains("RSA")) type = "RSA";
        else if (keyContent.contains("EC")) type = "EC";
        else if (keyContent.contains("DSA")) type = "DSA";
        else if (keyContent.contains("ED25519")) type = "ED25519";
        return String.format("SSH_KEY(type=%s, length=%d)", type, keyContent.length());
    }

    /**
     * Mask a credential string (generic): show first 2 and last 2 chars.
     * Example: "mySecretValue123" → "my**************23"
     */
    public static String maskCredential(String credential) {
        if (credential == null || credential.isBlank()) return "";
        if (credential.length() <= 4) return MASK;
        return credential.substring(0, 2) + "*".repeat(credential.length() - 4)
                + credential.substring(credential.length() - 2);
    }

    /**
     * Mask a database connection URL: show driver and host, mask password if present.
     * Example: "jdbc:postgresql://host:5432/db?password=secret" → "jdbc:postgresql://host:5432/db?password=****"
     */
    public static String maskJdbcUrl(String url) {
        if (url == null || url.isBlank()) return "";
        return url.replaceAll("(?i)(password=)[^&;]*", "$1****");
    }

    /**
     * Mask sensitive fields in a free-form string for safe logging.
     * Replaces password=xxx, token=xxx, key=xxx, etc. with masked values.
     * Example: "password=secret123 token=abc" → "password= **** token= ****"
     */
    public static String maskSensitiveFields(String input) {
        if (input == null || input.isBlank()) return input;
        return SENSITIVE_FIELD_PATTERN.matcher(input).replaceAll(m -> {
            String fullMatch = m.group(0);
            int eqIdx = fullMatch.indexOf('=');
            if (eqIdx < 0) eqIdx = fullMatch.indexOf(':');
            if (eqIdx < 0) return fullMatch;
            return fullMatch.substring(0, eqIdx + 1) + " " + MASK;
        });
    }

    /**
     * Mask an email address for display: user@domain.com → u***@***.com
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
     * Mask an IP address: 192.168.1.*** or similar.
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return ip;
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) return MASK;
        return ip.substring(0, lastDot + 1) + "***";
    }
}
