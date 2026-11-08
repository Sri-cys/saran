package com.psgitech.library.exception;

/**
 * Thrown when an incoming request exceeds the configured rate limit.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
