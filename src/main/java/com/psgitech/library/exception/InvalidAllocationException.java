package com.psgitech.library.exception;

/**
 * Thrown when an allocation operation is invalid due to state conflict,
 * such as unauthorized collection, attempting to release a collected record,
 * or non-eligible collection attempts.
 * Mapped to HTTP 409 CONFLICT.
 */
public class InvalidAllocationException extends RuntimeException {
    public InvalidAllocationException(String message) {
        super(message);
    }
}

