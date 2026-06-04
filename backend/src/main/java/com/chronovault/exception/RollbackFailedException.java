package com.chronovault.exception;

/**
 * Thrown when a rollback or restore operation fails.
 * Includes a reason code for structured error responses and to prevent
 * internal error details from leaking to the API client.
 */
public class RollbackFailedException extends RuntimeException {

    private final String reasonCode;

    public RollbackFailedException(String message) {
        super(message);
        this.reasonCode = "ROLLBACK_FAILED";
    }

    public RollbackFailedException(String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = "ROLLBACK_FAILED";
    }

    public RollbackFailedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}