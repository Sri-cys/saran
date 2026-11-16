package com.psgitech.library.enums;

/**
 * Type of loss or damage case.
 */
public enum CaseType {
    LOST,
    DAMAGED;

    public static CaseType fromString(String value) {
        if (value == null) return null;
        for (CaseType t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        return null;
    }
}
