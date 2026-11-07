package com.psgitech.library.concurrency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final LongAdder csrfFailures = new LongAdder();

    // Timing metrics
    private final LongAdder totalAdmissionWaitMs = new LongAdder();
    private final AtomicLong maxAdmissionWaitMs = new AtomicLong(0);
    private final LongAdder totalProcessingMs = new LongAdder();
    private final AtomicLong maxProcessingMs = new AtomicLong(0);

    // Feature Enhancement F7: Latency histogram & percentiles (5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000 ms)
    public static final long[] BUCKET_UPPER_BOUNDS = {5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000};
    private final LongAdder[] latencyBuckets;

    // Feature Enhancement F7: Rejection telemetry ring buffer (last 50 rejections)
    public static class RejectionEvent {
        private final long timestamp;
        private final String reason;
        private final String path;
        private final String operation;

        public RejectionEvent(String reason, String path, String operation) {
            this.timestamp = System.currentTimeMillis();
            this.reason = reason;
            this.path = path != null ? path : "/";
            this.operation = operation != null ? operation : "MUTATION";
        }

        public long getTimestamp() { return timestamp; }
        public String getReason() { return reason; }
        public String getPath() { return path; }
        public String getOperation() { return operation; }
    }

    private static final int MAX_REJECTIONS_BUFFER = 50;
    private final java.util.concurrent.ConcurrentLinkedDeque<RejectionEvent> recentRejections = new java.util.concurrent.ConcurrentLinkedDeque<>();

    // Pre-allocated, fully populated EnumMap
    private final Map<ConcurrencyOperation, OperationStats> operationStatsMap;

    public ConcurrencyMetrics() {
        Map<ConcurrencyOperation, OperationStats> map = new EnumMap<>(ConcurrencyOperation.class);
        for (ConcurrencyOperation op : ConcurrencyOperation.values()) {
            map.put(op, new OperationStats());
        }
        this.operationStatsMap = Collections.unmodifiableMap(map);

        this.latencyBuckets = new LongAdder[BUCKET_UPPER_BOUNDS.length + 1];
        for (int i = 0; i < latencyBuckets.length; i++) {
            latencyBuckets[i] = new LongAdder();
        }
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
        recordLatency(processingDurationMs);

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

    public void recordLatency(long latencyMs) {
        maxProcessingMs.accumulateAndGet(latencyMs, Math::max);
        for (int i = 0; i < BUCKET_UPPER_BOUNDS.length; i++) {
            if (latencyMs <= BUCKET_UPPER_BOUNDS[i]) {
                latencyBuckets[i].increment();
                return;
            }
        }
        latencyBuckets[BUCKET_UPPER_BOUNDS.length].increment();
    }

    public long getPercentileLatency(double percentile) {
        long total = 0;
        for (LongAdder b : latencyBuckets) total += b.sum();
        if (total == 0) return 0;

        long target = (long) Math.ceil((percentile / 100.0) * total);
        long cumulative = 0;
        for (int i = 0; i < BUCKET_UPPER_BOUNDS.length; i++) {
            cumulative += latencyBuckets[i].sum();
            if (cumulative >= target) {
                return BUCKET_UPPER_BOUNDS[i];
            }
        }
        return 5001; // overflow
    }

    public Map<String, Long> getLatencyHistogram() {
        Map<String, Long> hist = new LinkedHashMap<>();
        for (int i = 0; i < BUCKET_UPPER_BOUNDS.length; i++) {
            hist.put("<=" + BUCKET_UPPER_BOUNDS[i] + "ms", latencyBuckets[i].sum());
        }
        hist.put(">5000ms", latencyBuckets[BUCKET_UPPER_BOUNDS.length].sum());
        return hist;
    }

    public void recordRejection(ConcurrencyLimitExceededException.Reason reason) {
        recordRejection(reason, null, null);
    }

    public void recordRejection(ConcurrencyLimitExceededException.Reason reason, String path, ConcurrencyOperation op) {
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
        addRejectionEvent(new RejectionEvent(reason.name(), path, op != null ? op.name() : null));
    }

    public void recordCsrfFailure(String path) {
        csrfFailures.increment();
        addRejectionEvent(new RejectionEvent("CSRF_TOKEN_INVALID", path, "SECURITY"));
    }

    private void addRejectionEvent(RejectionEvent event) {
        recentRejections.addFirst(event);
        while (recentRejections.size() > MAX_REJECTIONS_BUFFER) {
            recentRejections.pollLast();
        }
    }

    public List<RejectionEvent> getRecentRejections() {
        return new ArrayList<>(recentRejections);
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
    public void recordCsrfFailure() { csrfFailures.increment(); }
    public long getCsrfFailures() { return csrfFailures.sum(); }
    public long getMaxAdmissionWaitMs() { return maxAdmissionWaitMs.get(); }
    public long getMaxProcessingMs() { return maxProcessingMs.get(); }

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

