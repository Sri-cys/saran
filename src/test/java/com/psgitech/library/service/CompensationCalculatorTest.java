package com.psgitech.library.service;

import com.psgitech.library.config.LossDamageConfig;
import com.psgitech.library.enums.CaseType;
import com.psgitech.library.enums.DamageSeverity;
import com.psgitech.library.exception.MissingReplacementPriceException;
import com.psgitech.library.model.CompensationBreakdown;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.*;

/**
 * Level 1 pure unit test suite for CompensationCalculator.
 * Validates deterministic depreciation rates, 50% cap, processing fee additions,
 * severity multipliers, HALF_UP rounding, and input guardrails using a fixed Clock.
 */
public class CompensationCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-16T12:00:00Z");
    private Clock fixedClock;
    private CompensationCalculator calculator;

    @Before
    public void setUp() {
        fixedClock = Clock.fixed(FIXED_NOW, ZONE);
        calculator = new CompensationCalculator(fixedClock);
    }

    @Test
    public void testZeroAgeNewBookCalculation() {
        LocalDate today = LocalDate.now(fixedClock);
        BigDecimal price = new BigDecimal("500.00");

        CompensationBreakdown b = calculator.calculate(CaseType.LOST, null, price, today);

        assertNotNull(b);
        assertEquals(new BigDecimal("500.00"), b.getPriceUsed());
        assertEquals(0, b.getAgeDays());
        assertFalse(b.isAgeFlag());
        assertEquals(new BigDecimal("0.0000"), b.getDepreciationApplied());
        assertEquals(new BigDecimal("50.00"), b.getProcessingFee());
        assertEquals(new BigDecimal("1.00"), b.getSeverityFraction());
        assertEquals(new BigDecimal("550.00"), b.getComputedAmount());
        assertEquals(new BigDecimal("550.00"), b.getFinalAmount());
    }

    @Test
    public void testOneYearAgeCalculation() {
        LocalDate today = LocalDate.now(fixedClock);
        LocalDate oneYearAgo = today.minusYears(1);
        BigDecimal price = new BigDecimal("500.00");

        CompensationBreakdown b = calculator.calculate(CaseType.LOST, null, price, oneYearAgo);

        // 1 year = 10% depreciation (0.1000)
        // Depreciated value = 450.00
        // Processing fee = 50.00
        // Total = 450.00 + 50.00 = 500.00
        assertEquals(new BigDecimal("0.1000"), b.getDepreciationApplied());
        assertFalse(b.isAgeFlag());
        assertEquals(new BigDecimal("500.00"), b.getFinalAmount());
    }

    @Test
    public void testThreeYearsAgeCalculation() {
        LocalDate today = LocalDate.now(fixedClock);
        LocalDate threeYearsAgo = today.minusDays(365 * 3);
        BigDecimal price = new BigDecimal("500.00");

        CompensationBreakdown b = calculator.calculate(CaseType.LOST, null, price, threeYearsAgo);

        // 3 years = 30% depreciation (0.3000)
        // Depreciated value = 350.00
        // Total = 350.00 + 50.00 = 400.00
        assertEquals(new BigDecimal("0.3000"), b.getDepreciationApplied());
        assertEquals(new BigDecimal("400.00"), b.getFinalAmount());
    }

    @Test
    public void testDepreciationCappedAtFiftyPercent() {
        LocalDate today = LocalDate.now(fixedClock);
        LocalDate sixYearsAgo = today.minusYears(6);
        BigDecimal price = new BigDecimal("500.00");

        CompensationBreakdown b = calculator.calculate(CaseType.LOST, null, price, sixYearsAgo);

        // 6 years would be 60%, but capped at 50% (0.5000)
        // Depreciated value = 250.00
        // Total = 250.00 + 50.00 = 300.00
        assertEquals(new BigDecimal("0.5000"), b.getDepreciationApplied());
        assertEquals(new BigDecimal("300.00"), b.getFinalAmount());
    }

    @Test
    public void testMissingAcquisitionDateSetsAgeFlag() {
        BigDecimal price = new BigDecimal("500.00");

        CompensationBreakdown b = calculator.calculate(CaseType.LOST, null, price, null);

        assertNotNull(b);
        assertTrue(b.isAgeFlag());
        assertEquals(0, b.getAgeDays());
        assertEquals(new BigDecimal("0.0000"), b.getDepreciationApplied());
        assertEquals(new BigDecimal("550.00"), b.getFinalAmount());
    }

    @Test
    public void testPartialDamageMinorSeverity() {
        LocalDate today = LocalDate.now(fixedClock);
        LocalDate oneYearAgo = today.minusYears(1);
        BigDecimal price = new BigDecimal("500.00");

        // Depreciated base = 450.00
        // MINOR severity = 0.25 -> 450 * 0.25 = 112.50
        // Total = 112.50 + 50.00 = 162.50
        CompensationBreakdown b = calculator.calculate(CaseType.DAMAGED, DamageSeverity.MINOR, price, oneYearAgo);

        assertEquals(new BigDecimal("0.25"), b.getSeverityFraction());
        assertEquals(new BigDecimal("162.50"), b.getFinalAmount());
    }

    @Test
    public void testPartialDamageMajorSeverity() {
        LocalDate today = LocalDate.now(fixedClock);
        LocalDate oneYearAgo = today.minusYears(1);
        BigDecimal price = new BigDecimal("500.00");

        // Depreciated base = 450.00
        // MAJOR severity = 0.60 -> 450 * 0.60 = 270.00
        // Total = 270.00 + 50.00 = 320.00
        CompensationBreakdown b = calculator.calculate(CaseType.DAMAGED, DamageSeverity.MAJOR, price, oneYearAgo);

        assertEquals(new BigDecimal("0.60"), b.getSeverityFraction());
        assertEquals(new BigDecimal("320.00"), b.getFinalAmount());
    }

    @Test
    public void testPartialDamageUnusableSeverity() {
        LocalDate today = LocalDate.now(fixedClock);
        LocalDate oneYearAgo = today.minusYears(1);
        BigDecimal price = new BigDecimal("500.00");

        // Depreciated base = 450.00
        // UNUSABLE severity = 1.00 -> 450 * 1.00 = 450.00
        // Total = 450.00 + 50.00 = 500.00
        CompensationBreakdown b = calculator.calculate(CaseType.DAMAGED, DamageSeverity.UNUSABLE, price, oneYearAgo);

        assertEquals(new BigDecimal("1.00"), b.getSeverityFraction());
        assertEquals(new BigDecimal("500.00"), b.getFinalAmount());
    }

    @Test
    public void testRoundingHalfUpPrecision() {
        LocalDate today = LocalDate.now(fixedClock);
        BigDecimal price = new BigDecimal("333.33");

        CompensationBreakdown b = calculator.calculate(CaseType.DAMAGED, DamageSeverity.MINOR, price, today);

        // 333.33 * 0.25 = 83.3325 -> rounds HALF_UP to 83.33
        // 83.33 + 50.00 = 133.33
        assertEquals(new BigDecimal("133.33"), b.getFinalAmount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullPriceRejected() {
        calculator.calculate(CaseType.LOST, null, null, LocalDate.now());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZeroPriceRejected() {
        calculator.calculate(CaseType.LOST, null, BigDecimal.ZERO, LocalDate.now());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativePriceRejected() {
        calculator.calculate(CaseType.LOST, null, new BigDecimal("-100.00"), LocalDate.now());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullTypeRejected() {
        calculator.calculate(null, DamageSeverity.MINOR, new BigDecimal("500.00"), LocalDate.now());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDamagedWithoutSeverityRejected() {
        calculator.calculate(CaseType.DAMAGED, null, new BigDecimal("500.00"), LocalDate.now());
    }

    @Test
    public void testCustomConfigInjection() {
        CompensationCalculator customCalc = new CompensationCalculator(
                new BigDecimal("75.00"), // ₹75 fee
                new BigDecimal("0.15"),  // 15% per year
                new BigDecimal("0.60"),  // max 60%
                fixedClock
        );

        LocalDate today = LocalDate.now(fixedClock);
        LocalDate twoYearsAgo = today.minusYears(2);
        BigDecimal price = new BigDecimal("1000.00");

        // 2 years * 15% = 30% -> 0.3000 depreciation
        // Depreciated base = 700.00
        // Processing fee = 75.00
        // Total = 775.00
        CompensationBreakdown b = customCalc.calculate(CaseType.LOST, null, price, twoYearsAgo);
        assertEquals(new BigDecimal("0.3000"), b.getDepreciationApplied());
        assertEquals(new BigDecimal("75.00"), b.getProcessingFee());
        assertEquals(new BigDecimal("775.00"), b.getFinalAmount());
    }
}
