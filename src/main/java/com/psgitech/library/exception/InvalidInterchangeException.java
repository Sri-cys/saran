package com.psgitech.library.exception;

/**
 * Thrown when an interchange operation violates state machine transition rules or request payload validation.
 * HTTP Mapping: 400 Bad Request or 409 Conflict.
 */
public class InvalidInterchangeException extends RuntimeException {
    public InvalidInterchangeException(String message) {
        super(message);
    }
}

