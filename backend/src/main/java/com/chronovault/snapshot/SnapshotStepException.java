package com.chronovault.snapshot;

/**
 * Exception thrown when a snapshot operation fails at a specific step.
 * Carries the step name so the frontend can show which step failed.
 */
class SnapshotStepException extends RuntimeException {
    private final String step;

    SnapshotStepException(String step, String message) {
        super(message);
        this.step = step;
    }

    SnapshotStepException(String step, String message, Throwable cause) {
        super(message, cause);
        this.step = step;
    }

    public String getStep() {
        return step;
    }
}
