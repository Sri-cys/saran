package com.psgitech.library.exception;

/**
 * Thrown when an interchange operation is forbidden by institutional borrowing rules
 * (e.g., attempting to transfer an overdue book, non-borrowed copy, or archived book).
 * HTTP Mapping: 409 Conflict.
 */
public class InterchangeNotAllowedException extends RuntimeException {
    public InterchangeNotAllowedException(String message) {
        super(message);
    }
}

