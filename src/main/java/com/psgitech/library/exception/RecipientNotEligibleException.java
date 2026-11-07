package com.psgitech.library.exception;

/**
 * Thrown when the designated interchange recipient fails borrowing eligibility checks
 * (e.g., reached active borrowing quota, has outstanding fines, or inactive account).
 * HTTP Mapping: 409 Conflict.
 */
public class RecipientNotEligibleException extends RuntimeException {
    public RecipientNotEligibleException(String message) {
        super(message);
    }
}

