package com.psgitech.library.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value Object encapsulating the result of an overdue fine computation
 * and the policy snapshot parameters applied at calculation time.
 * Demonstrates OOP Encapsulation and Immutability.
 */
public final class FineCalculation {
    private final int overdueDays;
    private final int chargeableDays;
    private final BigDecimal fineAmount;
    private final BigDecimal finePerDay;
    private final int gracePeriodDays;
    private final BigDecimal maxFineApplied;

    public FineCalculation(int overdueDays, int chargeableDays, BigDecimal fineAmount,
                           BigDecimal finePerDay, int gracePeriodDays, BigDecimal maxFineApplied) {
        this.overdueDays = Math.max(0, overdueDays);
        this.chargeableDays = Math.max(0, chargeableDays);
        this.fineAmount = (fineAmount != null) ? fineAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.finePerDay = (finePerDay != null) ? finePerDay.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.gracePeriodDays = Math.max(0, gracePeriodDays);
        this.maxFineApplied = (maxFineApplied != null) ? maxFineApplied.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public static FineCalculation zero(BigDecimal rate, int gracePeriod) {
        return new FineCalculation(0, 0, BigDecimal.ZERO, rate, gracePeriod, null);
    }

    public boolean isFineIncurred() {
        return fineAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public int getOverdueDays() {
        return overdueDays;
    }

    public int getChargeableDays() {
        return chargeableDays;
    }

    public BigDecimal getFineAmount() {
        return fineAmount;
    }

    public BigDecimal getFinePerDay() {
        return finePerDay;
    }

    public int getGracePeriodDays() {
        return gracePeriodDays;
    }

    public BigDecimal getMaxFineApplied() {
        return maxFineApplied;
    }

    @Override
    public String toString() {
        return "FineCalculation{" +
                "overdueDays=" + overdueDays +
                ", chargeableDays=" + chargeableDays +
                ", fineAmount=" + fineAmount +
                ", finePerDay=" + finePerDay +
                ", gracePeriodDays=" + gracePeriodDays +
                ", maxFineApplied=" + maxFineApplied +
                '}';
    }
}

