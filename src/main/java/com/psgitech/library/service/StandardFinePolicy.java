package com.psgitech.library.service;

import com.psgitech.library.config.FineConfig;
import com.psgitech.library.interfaces.FinePolicy;
import com.psgitech.library.model.FineCalculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Standard institutional fine calculation policy for PSG iTech Library.
 * Implements FinePolicy strategy interface:
 * - Rate: INR 2.00 per calendar day (configurable via FineConfig)
 * - Grace Period: 0 days (configurable via FineConfig)
 * - Cap: None by default (configurable via FineConfig)
 */
public class StandardFinePolicy implements FinePolicy {

    @Override
    public FineCalculation calculateFine(LocalDate dueDate, LocalDate returnDate) {
        BigDecimal rate = FineConfig.getFinePerDay();
        int gracePeriod = FineConfig.getGracePeriodDays();
        BigDecimal cap = FineConfig.getMaxFineCap();

        if (dueDate == null || returnDate == null || !returnDate.isAfter(dueDate)) {
            return FineCalculation.zero(rate, gracePeriod);
        }

        int overdueDays = (int) ChronoUnit.DAYS.between(dueDate, returnDate);
        int chargeableDays = Math.max(0, overdueDays - gracePeriod);

        if (chargeableDays == 0) {
            return new FineCalculation(overdueDays, 0, BigDecimal.ZERO, rate, gracePeriod, cap);
        }

        BigDecimal calculatedAmount = rate.multiply(BigDecimal.valueOf(chargeableDays)).setScale(2, RoundingMode.HALF_UP);

        if (cap != null && calculatedAmount.compareTo(cap) > 0) {
            calculatedAmount = cap.setScale(2, RoundingMode.HALF_UP);
        }

        return new FineCalculation(overdueDays, chargeableDays, calculatedAmount, rate, gracePeriod, cap);
    }
}

