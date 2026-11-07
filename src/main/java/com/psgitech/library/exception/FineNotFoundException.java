package com.psgitech.library.exception;

/**
 * Thrown when a fine record with the given ID cannot be found.
 * Maps to HTTP 404 Not Found.
 */
public class FineNotFoundException extends RuntimeException {
    public FineNotFoundException(String message) {
        super(message);
    }
}

