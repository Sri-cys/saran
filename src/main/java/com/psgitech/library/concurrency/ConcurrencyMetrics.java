package com.psgitech.library.concurrency;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * High-performance, lock-free in-memory concurrency metrics collector.
 * Tracks throughput, error distributions, wait times, and deadlock metrics.
 */
public class ConcurrencyMetrics implements TransactionRetryListener {

    public static class OperationStats {
        private final AtomicInteger active = new AtomicInteger(0);
        private final LongAdder completed = new LongAdder();
        private final LongAdder conflicts = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalWaitMs = new LongAdder();
        private final LongAdder totalProcessingMs = new LongAdder();

        public int getActive() {
            return active.get();
        }

        public long getCompleted() {
            return completed.sum();
        }

        public long getConflicts() {
            return conflicts.sum();
        }

        public long getErrors() {
            return errors.sum();
        }

        public double getAvgWaitMs() {
            long comp = completed.sum();
            return comp > 0 ? (double) totalWaitMs.sum() / comp : 0.0;
        }

        public double getAvgProcessingMs() {
            long comp = completed.sum();
            return comp > 0 ? (double) totalProcessingMs.sum() / comp : 0.0;
        }
    }

    private final Instant startTime = Instant.now();

    // High water marks
    private final AtomicInteger maxActiveObserved = new AtomicInteger(0);
    private final AtomicInteger maxWaitingObserved = new AtomicInteger(0);

    // Global counters
    private final LongAdder totalAdmitted = new LongAdder();
    private final LongAdder totalCompleted = new LongAdder();
    private final LongAdder totalConflicts = new LongAdder();
    private final LongAdder totalClientErrors = new LongAdder();
    private final LongAdder totalServerErrors = new LongAdder();

    // Rejections
    private final LongAdder rejectionsWaitTimeout = new LongAdder();
    private final LongAdder rejectionsWaitingRoomFull = new LongAdder();
    private final LongAdder rejectionsInterrupted = new LongAdder();
    private final LongAdder rejectionsUserCap = new LongAdder();
    private final LongAdder rejectionsPoolExhausted = new LongAdder();

    // Deadlock and lock timeout counters
    private final LongAdder deadlockRetries = new LongAdder();
    private final LongAdder lockWaitTimeouts = new LongAdder();

    // Timing metrics
    private final LongAdder totalAdmissionWaitMs = new LongAdder();
    private final AtomicLong maxAdmissionWaitMs = new AtomicLong(0);
    private final LongAdder totalProcessingMs = new LongAdder();

    // Pre-allocated, fully populated EnumMap
    private final Map<ConcurrencyOperation, OperationStats> operationStatsMap;

    public ConcurrencyMetrics() {
        Map<ConcurrencyOperation, OperationStats> map = new EnumMap<>(ConcurrencyOperation.class);
        for (ConcurrencyOperation op : ConcurrencyOperation.values()) {
            map.put(op, new OperationStats());
        }
        this.operationStatsMap = Collections.unmodifiableMap(map);
    }

    public void updateActiveWatermark(int currentActive) {
        maxActiveObserved.accumulateAndGet(currentActive, Math::max);
    }

    public void updateWaitingWatermark(int currentWaiting) {
        maxWaitingObserved.accumulateAndGet(currentWaiting, Math::max);
    }

    public void recordAdmitted(ConcurrencyOperation op, long waitDurationMs) {
        totalAdmitted.increment();
        totalAdmissionWaitMs.add(waitDurationMs);
        maxAdmissionWaitMs.accumulateAndGet(waitDurationMs, Math::max);

        OperationStats stats = operationStatsMap.get(op);
        if (stats != null) {
            stats.active.incrementAndGet();
            stats.totalWaitMs.add(waitDurationMs);
        }
    }

    public void recordCompleted(ConcurrencyOperation op, int statusCode, long processingDurationMs) {
        totalCompleted.increment();
        totalProcessingMs.add(processingDurationMs);

        OperationStats stats = operationStatsMap.get(op);
        if (stats != null) {
            stats.active.decrementAndGet();
            stats.completed.increment();
            stats.totalProcessingMs.add(processingDurationMs);
        }

        if (statusCode == 409) {
            totalConflicts.increment();
            if (stats != null) stats.conflicts.increment();
        } else if (statusCode >= 400 && statusCode < 500) {
            totalClientErrors.increment();
        } else if (statusCode >= 500) {
            totalServerErrors.increment();
            if (stats != null) stats.errors.increment();
        }
    }

    public void recordRejection(ConcurrencyLimitExceededException.Reason reason) {
        if (reason == null) return;
        switch (reason) {
            case WAIT_TIMEOUT:
                rejectionsWaitTimeout.increment();
                break;
            case WAITING_ROOM_FULL:
                rejectionsWaitingRoomFull.increment();
                break;
            case USER_INFLIGHT_CAP:
                rejectionsUserCap.increment();
                break;
            case POOL_EXHAUSTED:
                rejectionsPoolExhausted.increment();
                break;
        }
    }

    public void recordInterrupted() {
        rejectionsInterrupted.increment();
    }

    @Override
    public void onDeadlockRetry(String operationName, int attempt, long backoffMs) {
        deadlockRetries.increment();
    }

    @Override
    public void onLockWaitTimeout(String operationName, int attempt) {
        lockWaitTimeouts.increment();
    }

    // Getters
    public Instant getStartTime() { return startTime; }
    public int getMaxActiveObserved() { return maxActiveObserved.get(); }
    public int getMaxWaitingObserved() { return maxWaitingObserved.get(); }
    public long getTotalAdmitted() { return totalAdmitted.sum(); }
    public long getTotalCompleted() { return totalCompleted.sum(); }
    public long getTotalConflicts() { return totalConflicts.sum(); }
    public long getTotalClientErrors() { return totalClientErrors.sum(); }
    public long getTotalServerErrors() { return totalServerErrors.sum(); }
    public long getRejectionsWaitTimeout() { return rejectionsWaitTimeout.sum(); }
    public long getRejectionsWaitingRoomFull() { return rejectionsWaitingRoomFull.sum(); }
    public long getRejectionsInterrupted() { return rejectionsInterrupted.sum(); }
    public long getRejectionsUserCap() { return rejectionsUserCap.sum(); }
    public long getRejectionsPoolExhausted() { return rejectionsPoolExhausted.sum(); }
    public long getDeadlockRetries() { return deadlockRetries.sum(); }
    public long getLockWaitTimeouts() { return lockWaitTimeouts.sum(); }
    public long getMaxAdmissionWaitMs() { return maxAdmissionWaitMs.get(); }

    public double getAvgAdmissionWaitMs() {
        long count = totalAdmitted.sum();
        return count > 0 ? (double) totalAdmissionWaitMs.sum() / count : 0.0;
    }

    public double getAvgProcessingMs() {
        long count = totalCompleted.sum();
        return count > 0 ? (double) totalProcessingMs.sum() / count : 0.0;
    }

    public Map<ConcurrencyOperation, OperationStats> getOperationStatsMap() {
        return operationStatsMap;
    }
}

