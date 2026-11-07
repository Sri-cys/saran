package com.psgitech.library.exception;

public class BorrowTransactionNotFoundException extends RuntimeException {
    public BorrowTransactionNotFoundException(String message) {
        super(message);
    }
}

