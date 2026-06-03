package com.chronovault.exception;

import lombok.Getter;

/**
 * Unified error codes for all API responses.
 * Format: HTTP_STATUS_CODE + SEQUENTIAL_NUMBER
 * e.g., 40401 = 404 Not Found, first error in 404 category
 */
@Getter
public enum ErrorCode {
    // 400 Bad Request
    BAD_REQUEST(400, 40001, "请求参数错误"),
    VALIDATION_FAILED(400, 40002, "参数验证失败"),
    MISSING_PARAMETER(400, 40003, "缺少必需参数"),
    INVALID_TYPE(400, 40004, "参数类型错误"),
    INVALID_FORMAT(400, 40005, "请求体格式错误"),

    // 401 Unauthorized
    UNAUTHORIZED(401, 40101, "未授权访问"),
    INVALID_CREDENTIALS(401, 40102, "邮箱或密码错误"),
    TOKEN_EXPIRED(401, 40103, "Token 已过期"),
    TOKEN_INVALID(401, 40104, "Token 无效"),
    INVALID_API_KEY(401, 40105, "API Key 无效"),

    // 403 Forbidden
    FORBIDDEN(403, 40301, "无权限执行此操作"),
    INSUFFICIENT_ROLE(403, 40302, "角色权限不足"),

    // 404 Not Found
    NOT_FOUND(404, 40401, "资源不存在"),
    SERVER_NOT_FOUND(404, 40402, "服务器不存在"),
    SNAPSHOT_NOT_FOUND(404, 40403, "快照不存在"),
    STORAGE_NOT_FOUND(404, 40404, "存储目标不存在"),
    USER_NOT_FOUND(404, 40405, "用户不存在"),

    // 409 Conflict
    CONFLICT(409, 40901, "数据冲突"),
    DUPLICATE_ENTRY(409, 40902, "数据已存在"),

    // 429 Too Many Requests
    RATE_LIMITED(429, 42901, "请求过于频繁，请稍后再试"),

    // 500 Internal Server Error
    INTERNAL_ERROR(500, 50001, "服务器内部错误"),
    SSH_CONNECTION_FAILED(500, 50002, "SSH 连接失败"),
    RESTIC_OPERATION_FAILED(500, 50003, "备份操作失败"),
    SNAPSHOT_CREATE_FAILED(500, 50004, "快照创建失败"),
    ROLLBACK_FAILED(500, 50005, "回滚操作失败"),
    ENCRYPTION_FAILED(500, 50006, "加密操作失败"),

    // 503 Service Unavailable
    SERVICE_UNAVAILABLE(503, 50301, "服务暂时不可用"),
    AGENT_OFFLINE(503, 50302, "Agent 离线");

    private final int httpStatus;
    private final int code;
    private final String defaultMessage;

    ErrorCode(int httpStatus, int code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}