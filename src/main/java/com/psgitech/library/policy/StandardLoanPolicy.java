package com.psgitech.library.policy;

import com.psgitech.library.config.BorrowingPolicy;

/**
 * Concrete standard loan policy implementation.
 * Issues loans for the institutional standard borrowing period (14 days) and permits renewals.
 */
public class StandardLoanPolicy implements LoanPolicy {

    public static final String CODE = "STANDARD";
    public static final String LABEL = "Standard";

    private static final StandardLoanPolicy INSTANCE = new StandardLoanPolicy();

    public static StandardLoanPolicy getInstance() {
        return INSTANCE;
    }

    public StandardLoanPolicy() {
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public int getLoanDays() {
        return BorrowingPolicy.DEFAULT_BORROWING_DAYS;
    }

    @Override
    public boolean canRenew() {
        return true;
    }

    @Override
    public boolean isHighDemand() {
        return false;
    }

    @Override
    public String toString() {
        return "StandardLoanPolicy{code='" + CODE + "', days=" + getLoanDays() + ", canRenew=true}";
    }
}
