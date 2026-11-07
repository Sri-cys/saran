package com.psgitech.library.exception;

/**
 * Thrown when a student exceeds the allowed payment initiation rate limit.
 * Maps to HTTP 429 Too Many Requests.
 */
public class PaymentRateLimitExceededException extends RuntimeException {
    public PaymentRateLimitExceededException(String message) {
        super(message);
    }
}

