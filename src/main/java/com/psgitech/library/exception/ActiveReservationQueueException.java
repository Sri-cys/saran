package com.psgitech.library.exception;

/**
 * Thrown when a direct student-to-student interchange is blocked because other students
 * are waiting in the official reservation queue (WAITING or ALLOCATED priority).
 * HTTP Mapping: 409 Conflict.
 */
public class ActiveReservationQueueException extends RuntimeException {
    public ActiveReservationQueueException(String message) {
        super(message);
    }
}

