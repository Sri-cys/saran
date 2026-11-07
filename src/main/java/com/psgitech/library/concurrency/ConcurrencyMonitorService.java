package com.psgitech.library.concurrency;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service for aggregating operational concurrency and capacity metrics for Admin inspection.
 * Strictly guarantees that no personal identifiable data, passwords, or payment secrets are included.
 */
public class ConcurrencyMonitorService {

    private final ConcurrencyManager manager;

    public ConcurrencyMonitorService() {
        this.manager = ConcurrencyManager.getInstance();
    }

    public ConcurrencyMonitorService(ConcurrencyManager manager) {
        this.manager = manager;
    }

    public Map<String, Object> getStatusSnapshot() {
        ConcurrencyConfig config = manager.getConfig();
        ConcurrencyMetrics metrics = manager.getMetrics();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("since", DateTimeFormatter.ISO_INSTANT.format(metrics.getStartTime()));
        data.put("limiterEnabled", config.isLimiterEnabled());
        data.put("monitorEnabled", config.isMonitorEnabled());

        // Configuration
        Map<String, Object> configMap = new LinkedHashMap<>();
        configMap.put("maxConcurrentMutations", config.getMaxConcurrentMutations());
        configMap.put("maxWaitingMutations", config.getMaxWaitingMutations());
        configMap.put("waitTimeoutMs", config.getWaitTimeoutMs());
        configMap.put("userInflightCap", config.getUserInflightCap());
        data.put("config", configMap);

        // Current real-time gauges
        Map<String, Object> currentMap = new LinkedHashMap<>();
        currentMap.put("active", manager.getActiveRequests());
        currentMap.put("waiting", manager.getWaitingRequests());
        currentMap.put("availablePermits", manager.getAvailablePermits());
        currentMap.put("trackedUsers", manager.getTrackedUsersCount());
        currentMap.put("maxActiveObserved", metrics.getMaxActiveObserved());
        currentMap.put("maxWaitingObserved", metrics.getMaxWaitingObserved());
        data.put("current", currentMap);

        // Totals
        Map<String, Object> totalsMap = new LinkedHashMap<>();
        totalsMap.put("admitted", metrics.getTotalAdmitted());
        totalsMap.put("completed", metrics.getTotalCompleted());
        totalsMap.put("conflicts", metrics.getTotalConflicts());
        totalsMap.put("clientErrors", metrics.getTotalClientErrors());
        totalsMap.put("serverErrors", metrics.getTotalServerErrors());
        totalsMap.put("deadlockRetries", metrics.getDeadlockRetries());
        totalsMap.put("lockWaitTimeouts", metrics.getLockWaitTimeouts());

        Map<String, Object> rejectionsMap = new LinkedHashMap<>();
        rejectionsMap.put("waitTimeout", metrics.getRejectionsWaitTimeout());
        rejectionsMap.put("waitingRoomFull", metrics.getRejectionsWaitingRoomFull());
        rejectionsMap.put("interrupted", metrics.getRejectionsInterrupted());
        rejectionsMap.put("userCap", metrics.getRejectionsUserCap());
        rejectionsMap.put("poolExhausted", metrics.getRejectionsPoolExhausted());
        totalsMap.put("rejected", rejectionsMap);
        data.put("totals", totalsMap);

        // Timing
        Map<String, Object> timingMap = new LinkedHashMap<>();
        timingMap.put("avgAdmissionWaitMs", Math.round(metrics.getAvgAdmissionWaitMs() * 100.0) / 100.0);
        timingMap.put("maxAdmissionWaitMs", metrics.getMaxAdmissionWaitMs());
        timingMap.put("avgProcessingMs", Math.round(metrics.getAvgProcessingMs() * 100.0) / 100.0);
        data.put("timing", timingMap);

        // Per-operation breakdown
        Map<String, Object> operationsMap = new LinkedHashMap<>();
        for (Map.Entry<ConcurrencyOperation, ConcurrencyMetrics.OperationStats> entry : metrics.getOperationStatsMap().entrySet()) {
            ConcurrencyOperation op = entry.getKey();
            ConcurrencyMetrics.OperationStats stats = entry.getValue();

            Map<String, Object> opMap = new LinkedHashMap<>();
            opMap.put("active", stats.getActive());
            opMap.put("completed", stats.getCompleted());
            opMap.put("conflicts", stats.getConflicts());
            opMap.put("errors", stats.getErrors());
            opMap.put("avgWaitMs", Math.round(stats.getAvgWaitMs() * 100.0) / 100.0);
            opMap.put("avgProcessingMs", Math.round(stats.getAvgProcessingMs() * 100.0) / 100.0);
            opMap.put("description", op.getDescription());

            operationsMap.put(op.name(), opMap);
        }
        data.put("operations", operationsMap);

        return data;
    }
}

