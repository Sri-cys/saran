package com.psgitech.library.policy;

/**
 * Interface representing a dynamic borrowing duration and renewal strategy.
 * Demonstrates the Strategy pattern and runtime polymorphism for the exam-season dynamic policy.
 * Corresponds to BorrowingPolicy in the original architectural specification.
 */
public interface LoanPolicy {

    /**
     * Unique policy code identifying the active strategy (e.g., "STANDARD", "HIGH_DEMAND").
     */
    String getCode();

    /**
     * User-facing localized display label (e.g., "Standard", "High Demand").
     */
    String getLabel();

    /**
     * Allowed borrowing loan duration in days (e.g., 14 for Standard, 5 for High Demand).
     */
    int getLoanDays();

    /**
     * Whether loan extensions and renewals are permitted under this policy.
     */
    boolean canRenew();

    /**
     * Convenience flag indicating if this policy represents high-demand enforcement.
     */
    boolean isHighDemand();
}
