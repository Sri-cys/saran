package com.psgitech.library.util;

import com.psgitech.library.concurrency.TransactionRetryListener;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Functional transaction retry utility.
 * Automatically retries operations on MySQL deadlock (Error 1213 / SQLState 40001)
 * with randomized exponential backoff and listener notifications.
 */
public class TransactionRetryHelper {
    private static final Logger LOGGER = Logger.getLogger(TransactionRetryHelper.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final List<TransactionRetryListener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(TransactionRetryListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(TransactionRetryListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private static void notifyDeadlock(String op, int attempt, long backoff) {
        for (TransactionRetryListener l : listeners) {
            try {
                l.onDeadlockRetry(op, attempt, backoff);
            } catch (Exception ignored) {}
        }
    }

    private static void notifyLockTimeout(String op, int attempt) {
        for (TransactionRetryListener l : listeners) {
            try {
                l.onLockWaitTimeout(op, attempt);
            } catch (Exception ignored) {}
        }
    }

    @FunctionalInterface
    public interface TransactionOperation<T> {
        T execute() throws Exception;
    }

    public static <T> T executeWithRetry(TransactionOperation<T> operation, String operationName) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return operation.execute();
            } catch (SQLException e) {
                if (isDeadlock(e) && attempt <= MAX_RETRIES) {
                    long backoff = 25L * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextInt(20);
                    notifyDeadlock(operationName, attempt, backoff);
                    LOGGER.log(Level.WARNING, "Deadlock encountered during " + operationName +
                            " (attempt " + attempt + "/" + MAX_RETRIES + "). Retrying in " + backoff + "ms...", e);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Transaction retry interrupted", ie);
                    }
                    continue;
                }
                if (isLockTimeout(e)) {
                    notifyLockTimeout(operationName, attempt);
                    throw new RuntimeException("Database lock wait timeout during " + operationName + ". Please retry your request.", e);
                }
                throw new RuntimeException(e.getMessage(), e);
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SQLException && isDeadlock((SQLException) cause) && attempt <= MAX_RETRIES) {
                    long backoff = 25L * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextInt(20);
                    notifyDeadlock(operationName, attempt, backoff);
                    LOGGER.log(Level.WARNING, "Deadlock (wrapped) encountered during " + operationName +
                            " (attempt " + attempt + "/" + MAX_RETRIES + "). Retrying in " + backoff + "ms...", cause);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Transaction retry interrupted", ie);
                    }
                    continue;
                }
                if (cause instanceof SQLException && isLockTimeout((SQLException) cause)) {
                    notifyLockTimeout(operationName, attempt);
                }
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    public static boolean isDeadlock(SQLException e) {
        if (e == null) return false;
        int errorCode = e.getErrorCode();
        String sqlState = e.getSQLState();
        return errorCode == 1213 || "40001".equals(sqlState);
    }

    public static boolean isLockTimeout(SQLException e) {
        if (e == null) return false;
        int errorCode = e.getErrorCode();
        String sqlState = e.getSQLState();
        return errorCode == 1205 || ("HY000".equals(sqlState) && e.getMessage() != null && e.getMessage().toLowerCase().contains("lock wait timeout"));
    }
}
