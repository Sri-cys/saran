package com.psgitech.library.service;

import com.psgitech.library.config.LossDamageConfig;
import com.psgitech.library.enums.CaseType;
import com.psgitech.library.enums.DamageSeverity;
import com.psgitech.library.exception.MissingReplacementPriceException;
import com.psgitech.library.model.CompensationBreakdown;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure domain calculator for lost and damaged book compensation.
 * Uses BigDecimal with HALF_UP rounding exclusively.
 * No database access. Clock is injectable for deterministic testing.
 */
public class CompensationCalculator {
    private final Clock clock;
    private final BigDecimal customProcessingFee;
    private final BigDecimal customDepreciationRatePerYear;
    private final BigDecimal customMaxDepreciation;

    public CompensationCalculator() {
        this(Clock.systemDefaultZone());
    }

    public CompensationCalculator(Clock clock) {
        this(null, null, null, clock);
    }

    public CompensationCalculator(BigDecimal processingFee, BigDecimal depreciationRatePerYear, BigDecimal maxDepreciation, Clock clock) {
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.customProcessingFee = processingFee;
        this.customDepreciationRatePerYear = depreciationRatePerYear;
        this.customMaxDepreciation = maxDepreciation;
    }

    public CompensationBreakdown calculate(CaseType type, DamageSeverity severity,
                                          BigDecimal price, LocalDate acquiredDate) {
        return calculate(type, severity, price, acquiredDate, LocalDate.now(clock));
    }

    public CompensationBreakdown calculate(CaseType type, DamageSeverity severity,
                                          BigDecimal price, LocalDate acquiredDate, LocalDate asOfDate) {
        if (type == null) {
            throw new IllegalArgumentException("Case type must not be null.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MissingReplacementPriceException("Replacement price is missing or non-positive. Assessed replacement value must be provided.");
        }
        if (type == CaseType.DAMAGED && severity == null) {
            throw new IllegalArgumentException("Damage severity is required for damaged book assessment.");
        }
        if (asOfDate == null) {
            asOfDate = LocalDate.now(clock);
        }

        // 1. Calculate age in days and years
        int ageDays = 0;
        boolean ageFlag = false;
        BigDecimal ageYears = BigDecimal.ZERO;

        if (acquiredDate == null) {
            ageDays = 0;
            ageFlag = true;
            ageYears = BigDecimal.ZERO;
        } else {
            long days = ChronoUnit.DAYS.between(acquiredDate, asOfDate);
            if (days < 0) days = 0;
            ageDays = (int) days;
            ageFlag = false;
            ageYears = BigDecimal.valueOf(ageDays).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP);
        }

        // 2. Depreciation with maximum cap
        BigDecimal rate = customDepreciationRatePerYear != null ? customDepreciationRatePerYear : LossDamageConfig.getDepreciationRatePerYear();
        BigDecimal maxDepr = customMaxDepreciation != null ? customMaxDepreciation : LossDamageConfig.getMaxDepreciation();
        BigDecimal depreciation = rate.multiply(ageYears).setScale(4, RoundingMode.HALF_UP);
        if (depreciation.compareTo(maxDepr) > 0) {
            depreciation = maxDepr;
        }

        // 3. Depreciated value
        BigDecimal one = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        BigDecimal factor = one.subtract(depreciation);
        BigDecimal depreciatedValue = price.multiply(factor).setScale(2, RoundingMode.HALF_UP);

        // 4. Processing Fee
        BigDecimal fee = customProcessingFee != null ? customProcessingFee : LossDamageConfig.getProcessingFee();

        // 5. Final charge based on type and severity fraction
        BigDecimal severityFraction;
        BigDecimal computedAmount;

        if (type == CaseType.LOST) {
            severityFraction = BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
            computedAmount = depreciatedValue.add(fee).setScale(2, RoundingMode.HALF_UP);
        } else {
            severityFraction = severity.getFraction().setScale(2, RoundingMode.HALF_UP);
            BigDecimal damagedValue = depreciatedValue.multiply(severityFraction).setScale(2, RoundingMode.HALF_UP);
            computedAmount = damagedValue.add(fee).setScale(2, RoundingMode.HALF_UP);
        }

        return new CompensationBreakdown(
                type,
                price,
                ageDays,
                ageFlag,
                depreciation,
                fee,
                severity,
                severityFraction,
                computedAmount
        );
    }
}
