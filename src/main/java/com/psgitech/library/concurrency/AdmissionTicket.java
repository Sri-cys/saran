package com.psgitech.library.concurrency;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AutoCloseable admission permit token.
 * Guarantees that acquired semaphore permits and per-user slots are released exactly once.
 */
public class AdmissionTicket implements AutoCloseable {
    private final ConcurrencyManager manager;
    private final ConcurrencyOperation operation;
    private final Integer userId;
    private final long admissionTimeMs;
    private final long waitDurationMs;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public AdmissionTicket(ConcurrencyManager manager, ConcurrencyOperation operation,
                           Integer userId, long admissionTimeMs, long waitDurationMs) {
        this.manager = manager;
        this.operation = operation;
        this.userId = userId;
        this.admissionTimeMs = admissionTimeMs;
        this.waitDurationMs = waitDurationMs;
    }

    public ConcurrencyOperation getOperation() {
        return operation;
    }

    public Integer getUserId() {
        return userId;
    }

    public long getAdmissionTimeMs() {
        return admissionTimeMs;
    }

    public long getWaitDurationMs() {
        return waitDurationMs;
    }

    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Closes the ticket, releasing permits back to ConcurrencyManager exactly once.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            manager.releaseTicket(this);
        }
    }
}

