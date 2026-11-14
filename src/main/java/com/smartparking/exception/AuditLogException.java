package com.smartparking.exception;

/**
 * An audit log entry could not be written (or the hash chain could not be continued). For security-critical
 * operations this aborts the operation; it must never be swallowed silently.
 */
public class AuditLogException extends RuntimeException {
    public AuditLogException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuditLogException(String message) {
        super(message);
    }
}
