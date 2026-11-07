package com.psgitech.library.concurrency;

/**
 * Listener hook invoked by TransactionRetryHelper on transaction deadlock retries
 * and lock wait timeouts. Decoupled from repository code.
 */
public interface TransactionRetryListener {
    void onDeadlockRetry(String operationName, int attempt, long backoffMs);
    void onLockWaitTimeout(String operationName, int attempt);
}

