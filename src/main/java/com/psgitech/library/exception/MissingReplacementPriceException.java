package com.psgitech.library.exception;

public class MissingReplacementPriceException extends IllegalArgumentException {
    public MissingReplacementPriceException(String message) {
        super(message);
    }
}
