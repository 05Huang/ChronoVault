package com.chronovault.util;

import java.util.List;

public final class LogSanitizer {

    private static final List<String> SENSITIVE_PATTERNS = List.of(
            "RESTIC_PASSWORD=",
            "password=",
            "Password=",
            "api_key=",
            "apiKey=",
            "secret=",
            "token=",
            "SSH_KEY=",
            "credential="
    );

    private LogSanitizer() {}

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) return input;
        String result = input;
        for (String pattern : SENSITIVE_PATTERNS) {
            int idx = result.indexOf(pattern);
            while (idx >= 0) {
                int valueStart = idx + pattern.length();
                int valueEnd = findValueEnd(result, valueStart);
                if (valueEnd > valueStart) {
                    result = result.substring(0, valueStart) + "[REDACTED]" + result.substring(valueEnd);
                }
                idx = result.indexOf(pattern, valueStart + 10);
            }
        }
        return result;
    }

    public static String maskPassword(String password) {
        if (password == null || password.isBlank()) return "";
        return "[REDACTED]";
    }

    private static int findValueEnd(String input, int start) {
        for (int i = start; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\'' || c == '"') {
                return i;
            }
        }
        return input.length();
    }
}