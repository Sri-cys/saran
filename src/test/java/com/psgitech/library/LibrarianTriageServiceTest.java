package com.psgitech.library;

import com.psgitech.library.service.LibrarianTriageService;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class LibrarianTriageServiceTest {

    @Test
    public void testGetTriageSummaryReturnsAll5MetricCategories() {
        LibrarianTriageService service = new LibrarianTriageService();
        Map<String, Object> summary = service.getTriageSummary();

        assertNotNull(summary);
        assertTrue(summary.containsKey("expiringAllocationsCount"));
        assertTrue(summary.containsKey("stalledTransfersCount"));
        assertTrue(summary.containsKey("pendingLostDamagedCount"));
        assertTrue(summary.containsKey("retireEligibleCopiesCount"));
        assertTrue(summary.containsKey("criticalOverdueCount"));
        assertTrue(summary.containsKey("totalExceptions"));

        int total = ((Number) summary.get("totalExceptions")).intValue();
        assertTrue(total >= 0);
    }

    @Test
    public void testGetDrilldownHandlesSectionsWithoutError() {
        LibrarianTriageService service = new LibrarianTriageService();

        List<Map<String, Object>> allocs = service.getDrilldown("allocations");
        assertNotNull(allocs);

        List<Map<String, Object>> transfers = service.getDrilldown("transfers");
        assertNotNull(transfers);
        // Verify OTP codes and hashes are not exposed
        for (Map<String, Object> t : transfers) {
            assertFalse(t.containsKey("otp_code"));
            assertFalse(t.containsKey("otp_hash"));
        }

        List<Map<String, Object>> cases = service.getDrilldown("cases");
        assertNotNull(cases);

        List<Map<String, Object>> worn = service.getDrilldown("worn_copies");
        assertNotNull(worn);

        List<Map<String, Object>> overdue = service.getDrilldown("overdue");
        assertNotNull(overdue);
    }
}
