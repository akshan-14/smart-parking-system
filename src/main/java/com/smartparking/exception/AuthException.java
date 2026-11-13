package com.smartparking.exception;

/**
 * Thrown when user authentication fails.
 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}
