package com.psgitech.library.enums;

/**
 * Resolution choice opted by the student or designated by staff for resolving a lost/damaged book case.
 */
public enum ResolutionChoice {
    PAYMENT,
    REPLACEMENT;

    public static ResolutionChoice fromString(String value) {
        if (value == null) return null;
        for (ResolutionChoice c : values()) {
            if (c.name().equalsIgnoreCase(value.trim())) {
                return c;
            }
        }
        return null;
    }
}
