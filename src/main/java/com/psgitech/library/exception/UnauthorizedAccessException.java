package com.psgitech.library.exception;

/**
 * Thrown when an authenticated user attempts an operation outside their authorized role.
 */
public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }

    public UnauthorizedAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
