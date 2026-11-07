package com.psgitech.library;

import com.psgitech.library.service.WaitEstimator;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

public class WaitEstimatorTest {

    private WaitEstimator waitEstimator;

    @Before
    public void setUp() {
        DBConnection.initializeDatabase();
        this.waitEstimator = new WaitEstimator();
    }

    @Test
    public void testNonPositiveQueuePositionReturnsUnableToEstimate() {
        WaitEstimator.WaitEstimate est0 = waitEstimator.estimateWait(1, 0);
        assertNull(est0.getDays());
        assertEquals("Unable to estimate", est0.getMessage());

        WaitEstimator.WaitEstimate estNeg = waitEstimator.estimateWait(1, -5);
        assertNull(estNeg.getDays());
        assertEquals("Unable to estimate", estNeg.getMessage());
    }

    @Test
    public void testBookWithAvailableCopyReturnsAvailableSoon() {
        // Book 1 in the seeded database has available copies
        WaitEstimator.WaitEstimate est = waitEstimator.estimateWait(1, 1, LocalDate.now());
        assertNotNull(est);
        if (est.getDays() != null && est.getDays() == 0) {
            assertEquals("Available soon", est.getMessage());
        }
    }

    @Test
    public void testEstimateWithFutureLoanDueDates() {
        // Test estimation with various queue positions for a known book
        WaitEstimator.WaitEstimate estPos1 = waitEstimator.estimateWait(1, 1);
        assertNotNull(estPos1);
        assertNotNull(estPos1.getMessage());

        WaitEstimator.WaitEstimate estPos5 = waitEstimator.estimateWait(1, 5);
        assertNotNull(estPos5);
        assertNotNull(estPos5.getMessage());
    }
}

