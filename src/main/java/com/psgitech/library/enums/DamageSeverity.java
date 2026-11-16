package com.psgitech.library.enums;

import java.math.BigDecimal;

/**
 * Severity level of physical book damage.
 * Maps to fractional multiplier in compensation calculation.
 */
public enum DamageSeverity {
    MINOR(new BigDecimal("0.25")),
    MAJOR(new BigDecimal("0.60")),
    UNUSABLE(new BigDecimal("1.00"));

    private final BigDecimal fraction;

    DamageSeverity(BigDecimal fraction) {
        this.fraction = fraction;
    }

    public BigDecimal getFraction() {
        return fraction;
    }

    public static DamageSeverity fromString(String value) {
        if (value == null) return null;
        for (DamageSeverity s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return null;
    }
}
