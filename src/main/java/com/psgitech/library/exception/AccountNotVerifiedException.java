package com.psgitech.library.exception;

public class AccountNotVerifiedException extends RuntimeException {
    private final int userId;
    private final String email;

    public AccountNotVerifiedException(String message, int userId, String email) {
        super(message);
        this.userId = userId;
        this.email = email;
    }

    public int getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}

