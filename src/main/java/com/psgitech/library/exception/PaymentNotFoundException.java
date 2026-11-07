package com.psgitech.library.exception;

/**
 * Thrown when a payment attempt record cannot be found.
 * Maps to HTTP 404 Not Found.
 */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}

