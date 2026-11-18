package com.psgitech.library.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Thread-safe login throttling to protect against credential stuffing and brute-force attacks.
 * Tracks failed attempts per account identifier and per IP address without external dependencies.
 */
public class LoginThrottler {
    private static final Logger LOGGER = Logger.getLogger(LoginThrottler.class.getName());

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 minutes

    private static final Map<String, AttemptTracker> IDENTIFIER_ATTEMPTS = new ConcurrentHashMap<>();
    private static final Map<String, AttemptTracker> IP_ATTEMPTS = new ConcurrentHashMap<>();

    private static class AttemptTracker {
        int count;
        long lockedUntil;

        AttemptTracker() {
            this.count = 1;
            this.lockedUntil = 0;
        }
    }

    public static boolean isLocked(String identifier, String ipAddress) {
        long now = System.currentTimeMillis();

        if (identifier != null) {
            AttemptTracker tracker = IDENTIFIER_ATTEMPTS.get(identifier.trim().toLowerCase());
            if (tracker != null && tracker.lockedUntil > now) {
                return true;
            }
        }

        if (ipAddress != null) {
            AttemptTracker tracker = IP_ATTEMPTS.get(ipAddress.trim());
            if (tracker != null && tracker.lockedUntil > now) {
                return true;
            }
        }

        return false;
    }

    public static void recordFailure(String identifier, String ipAddress) {
        long now = System.currentTimeMillis();

        if (identifier != null) {
            String key = identifier.trim().toLowerCase();
            IDENTIFIER_ATTEMPTS.compute(key, (k, tracker) -> {
                if (tracker == null || tracker.lockedUntil <= now) {
                    AttemptTracker t = new AttemptTracker();
                    return t;
                }
                tracker.count++;
                if (tracker.count >= MAX_ATTEMPTS) {
                    tracker.lockedUntil = now + LOCKOUT_DURATION_MS;
                    LOGGER.warning("SECURITY LOCKOUT: Account [" + key + "] temporarily locked out due to " + tracker.count + " failed login attempts.");
                }
                return tracker;
            });
        }

        if (ipAddress != null) {
            String ip = ipAddress.trim();
            IP_ATTEMPTS.compute(ip, (k, tracker) -> {
                if (tracker == null || tracker.lockedUntil <= now) {
                    AttemptTracker t = new AttemptTracker();
                    return t;
                }
                tracker.count++;
                if (tracker.count >= MAX_ATTEMPTS) {
                    tracker.lockedUntil = now + LOCKOUT_DURATION_MS;
                    LOGGER.warning("SECURITY LOCKOUT: IP [" + ip + "] temporarily locked out due to " + tracker.count + " failed login attempts.");
                }
                return tracker;
            });
        }
    }

    public static void recordSuccess(String identifier, String ipAddress) {
        if (identifier != null) {
            IDENTIFIER_ATTEMPTS.remove(identifier.trim().toLowerCase());
        }
        if (ipAddress != null) {
            IP_ATTEMPTS.remove(ipAddress.trim());
        }
    }
}
