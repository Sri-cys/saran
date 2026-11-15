package com.psgitech.library.policy;

/**
 * Concrete high-demand loan policy implementation.
 * Applied dynamically during peak reservation queue or exam demand.
 * Issues shorter loans (default 5 days) and strictly disallows renewals.
 */
public class HighDemandLoanPolicy implements LoanPolicy {

    public static final String CODE = "HIGH_DEMAND";
    public static final String LABEL = "High Demand";
    public static final int DEFAULT_HIGH_DEMAND_DAYS = 5;

    private final int loanDays;

    public HighDemandLoanPolicy() {
        this(DEFAULT_HIGH_DEMAND_DAYS);
    }

    public HighDemandLoanPolicy(int loanDays) {
        if (loanDays < 2) {
            throw new IllegalArgumentException("High-demand loan days must be at least 2 days.");
        }
        this.loanDays = loanDays;
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
        return this.loanDays;
    }

    @Override
    public boolean canRenew() {
        return false;
    }

    @Override
    public boolean isHighDemand() {
        return true;
    }

    @Override
    public String toString() {
        return "HighDemandLoanPolicy{code='" + CODE + "', days=" + loanDays + ", canRenew=false}";
    }
}
