package com.psgitech.library.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe, bounded sliding/fixed-window rate limiter for autocomplete suggestions (Decision D11).
 * Limit: 20 requests per 10 seconds per session/client identifier.
 * Automatically evicts stale windows to prevent memory exhaustion under synthetic floods.
 */
public class AutocompleteRateLimiter {
    public static final int MAX_REQUESTS = 20;
    public static final long WINDOW_MS = 10_000L; // 10 seconds
    private static final int MAX_ENTRIES = 10_000;

    private static class WindowCounter {
        final long windowStart;
        final AtomicInteger count;

        WindowCounter(long windowStart, int initialCount) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(initialCount);
        }
    }

    private final ConcurrentHashMap<String, WindowCounter> clients = new ConcurrentHashMap<>();

    private static final AutocompleteRateLimiter INSTANCE = new AutocompleteRateLimiter();

    public static AutocompleteRateLimiter getInstance() {
        return INSTANCE;
    }

    /**
     * Checks whether a request is admitted.
     *
     * @param clientId session ID or remote IP
     * @return true if within limit, false if rate limit exceeded
     */
    public boolean allowRequest(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = "anonymous";
        }

        long now = System.currentTimeMillis();
        evictIfOversized(now);

        WindowCounter counter = clients.compute(clientId, (key, existing) -> {
            if (existing == null || (now - existing.windowStart) > WINDOW_MS) {
                return new WindowCounter(now, 1);
            } else {
                existing.count.incrementAndGet();
                return existing;
            }
        });

        return counter.count.get() <= MAX_REQUESTS;
    }

    /**
     * Evicts expired records if map exceeds maximum allowable entries.
     */
    private void evictIfOversized(long now) {
        if (clients.size() > MAX_ENTRIES) {
            clients.entrySet().removeIf(entry -> (now - entry.getValue().windowStart) > WINDOW_MS);
        }
    }

    /**
     * Visible for testing: resets client counts.
     */
    public void reset() {
        clients.clear();
    }

    public int getActiveClientCount() {
        return clients.size();
    }
}
