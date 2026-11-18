package com.psgitech.library.service;

import com.psgitech.library.repository.ActivityLogRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service for recording and retrieving audit activity log events.
 * Never logs passwords, OTPs, or authorization secrets.
 */
public class ActivityLogService {
    private final ActivityLogRepository repository;

    public ActivityLogService() {
        this.repository = new ActivityLogRepository();
    }

    public ActivityLogService(ActivityLogRepository repository) {
        this.repository = repository;
    }

    public void logEvent(String eventType, int actorId, String actorRole, String targetEntity,
                         String targetId, String details, String ipAddress) {
        // Redact any sensitive content defensively
        String safeDetails = details;
        if (safeDetails != null && (safeDetails.toLowerCase().contains("password") || safeDetails.toLowerCase().contains("otp"))) {
            safeDetails = "[REDACTED_SECURITY_DATA]";
        }
        repository.insert(eventType, actorId, actorRole, targetEntity, targetId, safeDetails, ipAddress);
    }

    public List<Map<String, Object>> getLogs(int page, int pageSize, String eventType, String targetEntity,
                                             Integer actorId, LocalDate fromDate, LocalDate toDate) {
        int clampedPage = Math.max(1, page);
        int clampedSize = Math.max(1, Math.min(100, pageSize));
        int offset = (clampedPage - 1) * clampedSize;
        return repository.findLogs(offset, clampedSize, eventType, targetEntity, actorId, fromDate, toDate);
    }

    public int countLogs(String eventType, String targetEntity, Integer actorId, LocalDate fromDate, LocalDate toDate) {
        return repository.countLogs(eventType, targetEntity, actorId, fromDate, toDate);
    }
}
