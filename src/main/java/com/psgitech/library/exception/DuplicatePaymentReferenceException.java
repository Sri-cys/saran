package com.psgitech.library.exception;

/**
 * Thrown when a UPI transaction reference ID / UTR has already been submitted on another payment.
 * Maps to HTTP 409 Conflict.
 */
public class DuplicatePaymentReferenceException extends RuntimeException {
    public DuplicatePaymentReferenceException(String message) {
        super(message);
    }
}

