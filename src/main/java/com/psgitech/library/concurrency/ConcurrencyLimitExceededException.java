package com.psgitech.library.concurrency;

/**
 * Exception thrown when a request cannot be admitted due to concurrency limits,
 * waiting-room capacity exhaustion, or per-user concurrency caps.
 */
public class ConcurrencyLimitExceededException extends RuntimeException {

    public enum Reason {
        WAIT_TIMEOUT(503, "System is currently busy. Please retry in a few moments."),
        WAITING_ROOM_FULL(503, "System is at maximum waiting capacity. Please retry shortly."),
        USER_INFLIGHT_CAP(429, "Too many concurrent requests from this user. Please wait for ongoing requests to complete."),
        POOL_EXHAUSTED(503, "Database connection pool is temporarily saturated. Please retry in a few moments.");

        private final int httpStatus;
        private final String defaultMessage;

        Reason(int httpStatus, String defaultMessage) {
            this.httpStatus = httpStatus;
            this.defaultMessage = defaultMessage;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }
    }

    private final Reason reason;
    private final int httpStatus;
    private final int retryAfterSeconds;

    public ConcurrencyLimitExceededException(Reason reason) {
        super(reason.getDefaultMessage());
        this.reason = reason;
        this.httpStatus = reason.getHttpStatus();
        this.retryAfterSeconds = 2;
    }

    public ConcurrencyLimitExceededException(Reason reason, String message) {
        super(message);
        this.reason = reason;
        this.httpStatus = reason.getHttpStatus();
        this.retryAfterSeconds = 2;
    }

    public Reason getReason() {
        return reason;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

