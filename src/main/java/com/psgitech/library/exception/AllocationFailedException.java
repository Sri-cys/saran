package com.psgitech.library.exception;

/**
 * Thrown when an atomic conditional update for allocation or physical copy
 * fails (affected rows == 0), indicating a concurrent modification.
 * Mapped to HTTP 409 CONFLICT or HTTP 500.
 */
public class AllocationFailedException extends RuntimeException {
    public AllocationFailedException(String message) {
        super(message);
    }

    public AllocationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

