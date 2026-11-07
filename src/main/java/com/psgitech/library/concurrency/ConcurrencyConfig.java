package com.psgitech.library.concurrency;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encapsulates concurrency management and admission control configuration.
 * Validates settings and falls back safely with a WARNING if any value is invalid.
 */
public class ConcurrencyConfig {
    private static final Logger LOGGER = Logger.getLogger(ConcurrencyConfig.class.getName());

    public static final boolean DEFAULT_LIMITER_ENABLED = true;
    public static final int DEFAULT_MAX_CONCURRENT_MUTATIONS = 12;
    public static final int DEFAULT_MAX_WAITING_MUTATIONS = 24;
    public static final long DEFAULT_WAIT_TIMEOUT_MS = 3000L;
    public static final int DEFAULT_USER_INFLIGHT_CAP = 3;
    public static final boolean DEFAULT_MONITOR_ENABLED = true;

    private final boolean limiterEnabled;
    private final int maxConcurrentMutations;
    private final int maxWaitingMutations;
    private final long waitTimeoutMs;
    private final int userInflightCap;
    private final boolean monitorEnabled;

    public ConcurrencyConfig(boolean limiterEnabled, int maxConcurrentMutations,
                             int maxWaitingMutations, long waitTimeoutMs,
                             int userInflightCap, boolean monitorEnabled) {
        this.limiterEnabled = limiterEnabled;
        this.maxConcurrentMutations = maxConcurrentMutations;
        this.maxWaitingMutations = maxWaitingMutations;
        this.waitTimeoutMs = waitTimeoutMs;
        this.userInflightCap = userInflightCap;
        this.monitorEnabled = monitorEnabled;
    }

    public static ConcurrencyConfig loadFromClasspath() {
        Properties props = new Properties();
        try (InputStream is = ConcurrencyConfig.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read db.properties for concurrency configuration, using defaults", e);
        }

        boolean limiterEnabled = parseBoolean(props, "concurrency.limiter.enabled", DEFAULT_LIMITER_ENABLED);
        int maxMutations = parseInt(props, "concurrency.max.mutations", DEFAULT_MAX_CONCURRENT_MUTATIONS, 1, 50);
        int maxWaiting = parseInt(props, "concurrency.max.waiting", DEFAULT_MAX_WAITING_MUTATIONS, maxMutations, 200);
        long waitTimeoutMs = parseLong(props, "concurrency.wait.timeout.ms", DEFAULT_WAIT_TIMEOUT_MS, 500L, 10000L);
        int userInflightCap = parseInt(props, "concurrency.user.inflight.cap", DEFAULT_USER_INFLIGHT_CAP, 1, 10);
        boolean monitorEnabled = parseBoolean(props, "concurrency.monitor.enabled", DEFAULT_MONITOR_ENABLED);

        return new ConcurrencyConfig(limiterEnabled, maxMutations, maxWaiting, waitTimeoutMs, userInflightCap, monitorEnabled);
    }

    private static boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String val = System.getProperty(key, props.getProperty(key));
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        val = val.trim().toLowerCase();
        if ("true".equals(val) || "1".equals(val) || "yes".equals(val)) {
            return true;
        }
        if ("false".equals(val) || "0".equals(val) || "no".equals(val)) {
            return false;
        }
        LOGGER.warning("Invalid boolean value for " + key + ": '" + val + "'. Using safe default: " + defaultValue);
        return defaultValue;
    }

    private static int parseInt(Properties props, String key, int defaultValue, int min, int max) {
        String val = System.getProperty(key, props.getProperty(key));
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(val.trim());
            if (parsed < min || parsed > max) {
                LOGGER.warning("Value for " + key + " (" + parsed + ") outside safe bounds [" + min + ", " + max + "]. Using safe default: " + defaultValue);
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid integer for " + key + ": '" + val + "'. Using safe default: " + defaultValue);
            return defaultValue;
        }
    }

    private static long parseLong(Properties props, String key, long defaultValue, long min, long max) {
        String val = System.getProperty(key, props.getProperty(key));
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(val.trim());
            if (parsed < min || parsed > max) {
                LOGGER.warning("Value for " + key + " (" + parsed + ") outside safe bounds [" + min + ", " + max + "]. Using safe default: " + defaultValue);
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid long for " + key + ": '" + val + "'. Using safe default: " + defaultValue);
            return defaultValue;
        }
    }

    public boolean isLimiterEnabled() {
        return limiterEnabled;
    }

    public int getMaxConcurrentMutations() {
        return maxConcurrentMutations;
    }

    public int getMaxWaitingMutations() {
        return maxWaitingMutations;
    }

    public long getWaitTimeoutMs() {
        return waitTimeoutMs;
    }

    public int getUserInflightCap() {
        return userInflightCap;
    }

    public boolean isMonitorEnabled() {
        return monitorEnabled;
    }
}

