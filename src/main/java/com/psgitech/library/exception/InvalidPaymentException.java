package com.psgitech.library.exception;

/**
 * Thrown when an invalid payment operation, transition, or amount is attempted.
 * Maps to HTTP 400 Bad Request or 409 Conflict.
 */
public class InvalidPaymentException extends RuntimeException {
    public InvalidPaymentException(String message) {
        super(message);
    }
}

