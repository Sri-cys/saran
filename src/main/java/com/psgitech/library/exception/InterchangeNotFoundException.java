package com.psgitech.library.exception;

/**
 * Thrown when a requested book interchange record does not exist.
 * HTTP Mapping: 404 Not Found.
 */
public class InterchangeNotFoundException extends RuntimeException {
    public InterchangeNotFoundException(String message) {
        super(message);
    }
}

