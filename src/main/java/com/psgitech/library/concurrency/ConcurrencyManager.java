package com.psgitech.library.concurrency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core admission controller for concurrent mutating requests.
 * Uses a fair, timed Semaphore, a bounded waiting room, per-user in-flight tracking,
 * and thread-safe operational metrics.
 */
public class ConcurrencyManager {
    private static final Logger LOGGER = Logger.getLogger(ConcurrencyManager.class.getName());

    private static volatile ConcurrencyManager instance;

    private final ConcurrencyConfig config;
    private final Semaphore semaphore;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicInteger waitingRequests = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, AtomicInteger> userInflightMap = new ConcurrentHashMap<>();
    private final ConcurrencyMetrics metrics;

    public ConcurrencyManager(ConcurrencyConfig config) {
        this.config = config != null ? config : ConcurrencyConfig.loadFromClasspath();
        this.semaphore = new Semaphore(this.config.getMaxConcurrentMutations(), true);
        this.metrics = new ConcurrencyMetrics();
    }

    public static ConcurrencyManager getInstance() {
        if (instance == null) {
            synchronized (ConcurrencyManager.class) {
                if (instance == null) {
                    instance = new ConcurrencyManager(ConcurrencyConfig.loadFromClasspath());
                }
            }
        }
        return instance;
    }

    public static void setInstance(ConcurrencyManager customInstance) {
        synchronized (ConcurrencyManager.class) {
            instance = customInstance;
        }
    }

    /**
     * Attempts to admit a mutating request.
     * Enforces per-user concurrency cap, waiting-room bounds, and fair timed permit acquisition.
     */
    public AdmissionTicket acquireTicket(ConcurrencyOperation operation, Integer userId) {
        if (!config.isLimiterEnabled()) {
            // Limiter disabled via kill switch: pass through without permit consumption
            metrics.recordAdmitted(operation, 0L);
            return new AdmissionTicket(this, operation, userId, System.currentTimeMillis(), 0L);
        }

        // 1. Check per-user in-flight cap (if authenticated)
        if (userId != null && userId > 0) {
            AtomicInteger userCounter = userInflightMap.computeIfAbsent(userId, k -> new AtomicInteger(0));
            int current = userCounter.incrementAndGet();
            if (current > config.getUserInflightCap()) {
                // Exceeded per-user cap
                decrementUserCount(userId);
                metrics.recordRejection(ConcurrencyLimitExceededException.Reason.USER_INFLIGHT_CAP);
                throw new ConcurrencyLimitExceededException(ConcurrencyLimitExceededException.Reason.USER_INFLIGHT_CAP);
            }
        }

        // 2. Check waiting room capacity
        int currentWaiting = waitingRequests.incrementAndGet();
        metrics.updateWaitingWatermark(currentWaiting);

        if (currentWaiting > config.getMaxWaitingMutations()) {
            // Waiting room full: fail fast immediately
            waitingRequests.decrementAndGet();
            if (userId != null && userId > 0) {
                decrementUserCount(userId);
            }
            metrics.recordRejection(ConcurrencyLimitExceededException.Reason.WAITING_ROOM_FULL);
            throw new ConcurrencyLimitExceededException(ConcurrencyLimitExceededException.Reason.WAITING_ROOM_FULL);
        }

        long waitStartTime = System.currentTimeMillis();
        boolean acquired = false;

        try {
            acquired = semaphore.tryAcquire(config.getWaitTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            waitingRequests.decrementAndGet();
            if (userId != null && userId > 0) {
                decrementUserCount(userId);
            }
            metrics.recordInterrupted();
            throw new ConcurrencyLimitExceededException(ConcurrencyLimitExceededException.Reason.WAIT_TIMEOUT,
                    "Request interrupted while waiting for execution capacity.");
        }

        long waitDuration = System.currentTimeMillis() - waitStartTime;
        waitingRequests.decrementAndGet();

        if (!acquired) {
            if (userId != null && userId > 0) {
                decrementUserCount(userId);
            }
            metrics.recordRejection(ConcurrencyLimitExceededException.Reason.WAIT_TIMEOUT);
            throw new ConcurrencyLimitExceededException(ConcurrencyLimitExceededException.Reason.WAIT_TIMEOUT);
        }

        // Successfully acquired permit
        int currentActive = activeRequests.incrementAndGet();
        metrics.updateActiveWatermark(currentActive);
        metrics.recordAdmitted(operation, waitDuration);

        return new AdmissionTicket(this, operation, userId, System.currentTimeMillis(), waitDuration);
    }

    /**
     * Releases an admitted ticket back to the semaphore and decrements per-user counters.
     */
    void releaseTicket(AdmissionTicket ticket) {
        if (!config.isLimiterEnabled()) {
            return;
        }

        activeRequests.decrementAndGet();
        semaphore.release();

        Integer userId = ticket.getUserId();
        if (userId != null && userId > 0) {
            decrementUserCount(userId);
        }
    }

    private void decrementUserCount(int userId) {
        userInflightMap.computeIfPresent(userId, (id, counter) -> {
            int val = counter.decrementAndGet();
            return val <= 0 ? null : counter; // Remove entry from map if 0
        });
    }

    public ConcurrencyConfig getConfig() {
        return config;
    }

    public ConcurrencyMetrics getMetrics() {
        return metrics;
    }

    public int getActiveRequests() {
        return activeRequests.get();
    }

    public int getWaitingRequests() {
        return waitingRequests.get();
    }

    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    public int getTrackedUsersCount() {
        return userInflightMap.size();
    }
}
