package com.psgitech.library.exception;

/**
 * Thrown when an allocation record is not found by ID or copy/reservation lookup.
 * Mapped to HTTP 404 NOT FOUND.
 */
public class AllocationNotFoundException extends RuntimeException {
    public AllocationNotFoundException(String message) {
        super(message);
    }
}

