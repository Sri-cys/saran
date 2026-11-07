package com.psgitech.library.exception;

/**
 * Thrown when an illegal fine state mutation, excessive payment, or invalid waiver is attempted.
 * Maps to HTTP 409 Conflict.
 */
public class InvalidFineOperationException extends RuntimeException {
    public InvalidFineOperationException(String message) {
        super(message);
    }
}

