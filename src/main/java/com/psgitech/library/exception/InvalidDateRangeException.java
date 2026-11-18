package com.psgitech.library.exception;

/**
 * Thrown when an invalid date range is specified (e.g. fromDate > toDate or range exceeds maximum allowed span).
 */
public class InvalidDateRangeException extends RuntimeException {
    public InvalidDateRangeException(String message) {
        super(message);
    }

    public InvalidDateRangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
