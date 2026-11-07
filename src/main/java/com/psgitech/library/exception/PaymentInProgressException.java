package com.psgitech.library.exception;

/**
 * Thrown when an active payment is already in progress for a fine.
 * Maps to HTTP 409 Conflict.
 */
public class PaymentInProgressException extends RuntimeException {
    public PaymentInProgressException(String message) {
        super(message);
    }
}

