package com.smartparking.exception;

import java.time.LocalDateTime;

/**
 * Thrown when an account is locked due to repeated failed login attempts.
 */
public class AccountLockedException extends RuntimeException {
    private final LocalDateTime lockedUntil;

    public AccountLockedException(String message, LocalDateTime lockedUntil) {
        super(message);
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
}
