package com.psgitech.library;

import com.psgitech.library.concurrency.ConcurrencyConfig;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConcurrencyConfigTest {

    @Test
    public void testDefaultConfigValues() {
        ConcurrencyConfig config = new ConcurrencyConfig(true, 12, 24, 3000L, 3, true);
        assertTrue(config.isLimiterEnabled());
        assertEquals(12, config.getMaxConcurrentMutations());
        assertEquals(24, config.getMaxWaitingMutations());
        assertEquals(3000L, config.getWaitTimeoutMs());
        assertEquals(3, config.getUserInflightCap());
        assertTrue(config.isMonitorEnabled());
    }

    @Test
    public void testSafeBoundsAndFallbacks() {
        // Test loading with invalid system properties
        System.setProperty("concurrency.max.mutations", "-5");
        System.setProperty("concurrency.wait.timeout.ms", "99999999");
        System.setProperty("concurrency.user.inflight.cap", "100");

        try {
            ConcurrencyConfig loaded = ConcurrencyConfig.loadFromClasspath();
            // Should have safely fallen back to valid defaults
            assertEquals(ConcurrencyConfig.DEFAULT_MAX_CONCURRENT_MUTATIONS, loaded.getMaxConcurrentMutations());
            assertEquals(ConcurrencyConfig.DEFAULT_WAIT_TIMEOUT_MS, loaded.getWaitTimeoutMs());
            assertEquals(ConcurrencyConfig.DEFAULT_USER_INFLIGHT_CAP, loaded.getUserInflightCap());
        } finally {
            System.clearProperty("concurrency.max.mutations");
            System.clearProperty("concurrency.wait.timeout.ms");
            System.clearProperty("concurrency.user.inflight.cap");
        }
    }
}

