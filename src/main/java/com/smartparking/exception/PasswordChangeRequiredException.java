package com.smartparking.exception;

/**
 * Thrown when a user whose account is flagged force_password_change tries to use normal application functionality
 * before setting a new password. It is a {@link SecurityException}, so every existing access-denied path treats it
 * as a denial.
 */
public class PasswordChangeRequiredException extends SecurityException {
    public PasswordChangeRequiredException(String message) {
        super(message);
    }
}
