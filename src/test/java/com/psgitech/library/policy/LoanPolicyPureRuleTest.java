package com.psgitech.library.policy;

import com.psgitech.library.model.LoanPolicySettings;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pure unit tests verifying the hysteresis state transition logic and settings validation (Feature 12).
 * Runs completely in memory with zero database dependencies.
 */
public class LoanPolicyPureRuleTest {

    @Test
    public void testHysteresisSequence_StandardToHighDemandAndBack() {
        // Given default thresholds: enter = 4, exit = 1, days = 5
        LoanPolicySettings settings = new LoanPolicySettings(true, 4, 1, 5, 1, 1, null);

        // Sequence: 0 -> 3 -> 4 -> 5 -> 3 -> 2 -> 1 -> 0
        LoanPolicy current = new StandardLoanPolicy();

        // 1. Demand = 0 -> STANDARD
        current = PolicyTransitionRule.determineNextPolicy(current, 0, settings);
        assertFalse(current.isHighDemand());
        assertEquals("STANDARD", current.getCode());
        assertEquals(14, current.getLoanDays());
        assertTrue(current.canRenew());

        // 2. Demand = 3 -> Remains STANDARD (below enter threshold of 4)
        current = PolicyTransitionRule.determineNextPolicy(current, 3, settings);
        assertFalse(current.isHighDemand());
        assertEquals("STANDARD", current.getCode());

        // 3. Demand = 4 -> Transitions to HIGH_DEMAND (meets enter threshold >= 4)
        current = PolicyTransitionRule.determineNextPolicy(current, 4, settings);
        assertTrue(current.isHighDemand());
        assertEquals("HIGH_DEMAND", current.getCode());
        assertEquals(5, current.getLoanDays());
        assertFalse(current.canRenew());

        // 4. Demand = 5 -> Remains HIGH_DEMAND
        current = PolicyTransitionRule.determineNextPolicy(current, 5, settings);
        assertTrue(current.isHighDemand());
        assertEquals("HIGH_DEMAND", current.getCode());

        // 5. Demand = 3 -> Hysteresis dampening: Remains HIGH_DEMAND (3 > exit threshold of 1)
        current = PolicyTransitionRule.determineNextPolicy(current, 3, settings);
        assertTrue(current.isHighDemand());
        assertEquals("HIGH_DEMAND", current.getCode());

        // 6. Demand = 2 -> Hysteresis dampening: Remains HIGH_DEMAND (2 > exit threshold of 1)
        current = PolicyTransitionRule.determineNextPolicy(current, 2, settings);
        assertTrue(current.isHighDemand());
        assertEquals("HIGH_DEMAND", current.getCode());

        // 7. Demand = 1 -> Transitions back to STANDARD (demand <= exit threshold of 1)
        current = PolicyTransitionRule.determineNextPolicy(current, 1, settings);
        assertFalse(current.isHighDemand());
        assertEquals("STANDARD", current.getCode());
        assertEquals(14, current.getLoanDays());
        assertTrue(current.canRenew());

        // 8. Demand = 0 -> Remains STANDARD
        current = PolicyTransitionRule.determineNextPolicy(current, 0, settings);
        assertFalse(current.isHighDemand());
        assertEquals("STANDARD", current.getCode());
    }

    @Test
    public void testPolicyToggle_WhenDisabledAlwaysReturnsStandard() {
        // Disabled policy settings
        LoanPolicySettings disabledSettings = new LoanPolicySettings(false, 4, 1, 5, 1, 1, null);
        LoanPolicy current = new StandardLoanPolicy();

        // Even with huge queue demand (e.g. 50 reservations), disabled policy never throttles
        LoanPolicy next = PolicyTransitionRule.determineNextPolicy(current, 50, disabledSettings);
        assertFalse(next.isHighDemand());
        assertEquals("STANDARD", next.getCode());
        assertEquals(14, next.getLoanDays());
        assertTrue(next.canRenew());

        // Even if previously in HighDemand, when disabled it reverts to Standard
        LoanPolicy highDemand = new HighDemandLoanPolicy(5);
        LoanPolicy reverted = PolicyTransitionRule.determineNextPolicy(highDemand, 50, disabledSettings);
        assertFalse(reverted.isHighDemand());
        assertEquals("STANDARD", reverted.getCode());
    }

    @Test
    public void testExamWindowThresholdOverride() {
        LoanPolicySettings baseSettings = new LoanPolicySettings(true, 4, 1, 5, 1, 1, null);

        // During exam season, enter threshold is lowered to 2
        LoanPolicySettings examSettings = baseSettings.withEnterThresholdOverride(2);
        assertEquals(2, examSettings.getEnterThreshold());
        assertEquals(1, examSettings.getExitThreshold());

        LoanPolicy current = new StandardLoanPolicy();

        // Demand = 2 triggers HIGH_DEMAND under exam window override
        current = PolicyTransitionRule.determineNextPolicy(current, 2, examSettings);
        assertTrue("Demand 2 should trigger high demand during exam window", current.isHighDemand());
        assertEquals("HIGH_DEMAND", current.getCode());
    }

    @Test
    public void testSettingsValidation_ValidInputs() {
        // Default valid parameters
        LoanPolicySettings.validate(4, 1, 5, 1);
        LoanPolicySettings.validate(2, 0, 2, 10);
        LoanPolicySettings.validate(20, 19, 13, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSettingsValidation_EnterThresholdTooLow() {
        LoanPolicySettings.validate(1, 0, 5, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSettingsValidation_ExitThresholdNegative() {
        LoanPolicySettings.validate(4, -1, 5, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSettingsValidation_ExitThresholdEqualOrGreaterThanEnter() {
        LoanPolicySettings.validate(4, 4, 5, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSettingsValidation_HighDemandDaysTooLow() {
        LoanPolicySettings.validate(4, 1, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSettingsValidation_HighDemandDaysTooHigh() {
        LoanPolicySettings.validate(4, 1, 14, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSettingsValidation_InvalidVersion() {
        LoanPolicySettings.validate(4, 1, 5, 0);
    }

    @Test
    public void testStandardLoanPolicyCharacteristics() {
        LoanPolicy p = new StandardLoanPolicy();
        assertEquals("STANDARD", p.getCode());
        assertEquals("Standard", p.getLabel());
        assertEquals(14, p.getLoanDays());
        assertTrue(p.canRenew());
        assertFalse(p.isHighDemand());
    }

    @Test
    public void testHighDemandLoanPolicyCharacteristics() {
        LoanPolicy p = new HighDemandLoanPolicy(5);
        assertEquals("HIGH_DEMAND", p.getCode());
        assertEquals("High Demand", p.getLabel());
        assertEquals(5, p.getLoanDays());
        assertFalse(p.canRenew());
        assertTrue(p.isHighDemand());
    }
}
