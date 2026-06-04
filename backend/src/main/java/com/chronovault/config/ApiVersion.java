package com.chronovault.config;

/**
 * API version constants. Use these in @RequestMapping to ensure all endpoints
 * are versioned under a consistent prefix.
 *
 * <p>Current: {@value #V1}
 * <p>To add a new version, add a new constant and duplicate/modify the relevant controllers.</p>
 */
public final class ApiVersion {

    public static final String V1 = "/api/v1";

    private ApiVersion() {}
}
