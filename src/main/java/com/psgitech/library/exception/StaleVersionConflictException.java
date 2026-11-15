package com.psgitech.library.exception;

/**
 * Exception thrown when an optimistic locking version mismatch occurs during configuration updates.
 * Maps directly to HTTP 409 Conflict.
 */
public class StaleVersionConflictException extends RuntimeException {
    public StaleVersionConflictException(String message) {
        super(message);
    }

    public StaleVersionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
