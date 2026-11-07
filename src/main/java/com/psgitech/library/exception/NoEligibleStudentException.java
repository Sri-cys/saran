package com.psgitech.library.exception;

/**
 * Internal domain signal thrown when the FIFO reservation queue contains no
 * eligible waiting students for an incoming returned physical copy.
 * Never exposed to end users; caught internally by AllocationService to transition
 * the copy to AVAILABLE status.
 */
public class NoEligibleStudentException extends RuntimeException {
    public NoEligibleStudentException(String message) {
        super(message);
    }
}

