package com.psgitech.library.config;

import java.math.BigDecimal;

/**
 * Institutional configuration settings for Feature 14: Lost and Damaged Book Replacement.
 */
public final class LossDamageConfig {
    public static final BigDecimal DEFAULT_PROCESSING_FEE = new BigDecimal("50.00");
    public static final BigDecimal DEFAULT_DEPRECIATION_RATE_PER_YEAR = new BigDecimal("0.10");
    public static final BigDecimal DEFAULT_MAX_DEPRECIATION = new BigDecimal("0.50");
    public static final int DEFAULT_REPLACEMENT_DEADLINE_DAYS = 30;

    private static BigDecimal processingFee = DEFAULT_PROCESSING_FEE;
    private static BigDecimal depreciationRatePerYear = DEFAULT_DEPRECIATION_RATE_PER_YEAR;
    private static BigDecimal maxDepreciation = DEFAULT_MAX_DEPRECIATION;
    private static int replacementDeadlineDays = DEFAULT_REPLACEMENT_DEADLINE_DAYS;

    private LossDamageConfig() {}

    public static BigDecimal getProcessingFee() { return processingFee; }
    public static void setProcessingFee(BigDecimal fee) {
        if (fee == null || fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Processing fee must be non-negative.");
        }
        processingFee = fee;
    }

    public static BigDecimal getDepreciationRatePerYear() { return depreciationRatePerYear; }
    public static void setDepreciationRatePerYear(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Depreciation rate must be between 0.0 and 1.0.");
        }
        depreciationRatePerYear = rate;
    }

    public static BigDecimal getMaxDepreciation() { return maxDepreciation; }
    public static void setMaxDepreciation(BigDecimal max) {
        if (max == null || max.compareTo(BigDecimal.ZERO) < 0 || max.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Max depreciation must be between 0.0 and 1.0.");
        }
        maxDepreciation = max;
    }

    public static int getReplacementDeadlineDays() { return replacementDeadlineDays; }
    public static void setReplacementDeadlineDays(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Replacement deadline days must be at least 1.");
        }
        replacementDeadlineDays = days;
    }

    public static void resetToDefaults() {
        processingFee = DEFAULT_PROCESSING_FEE;
        depreciationRatePerYear = DEFAULT_DEPRECIATION_RATE_PER_YEAR;
        maxDepreciation = DEFAULT_MAX_DEPRECIATION;
        replacementDeadlineDays = DEFAULT_REPLACEMENT_DEADLINE_DAYS;
    }
}
