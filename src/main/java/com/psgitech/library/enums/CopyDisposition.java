package com.psgitech.library.enums;

/**
 * Physical disposition of a damaged book copy following return assessment.
 */
public enum CopyDisposition {
    WITHDRAWN,
    RETURNED_TO_CIRCULATION;

    public static CopyDisposition fromString(String value) {
        if (value == null) return null;
        for (CopyDisposition d : values()) {
            if (d.name().equalsIgnoreCase(value.trim())) {
                return d;
            }
        }
        return null;
    }
}
