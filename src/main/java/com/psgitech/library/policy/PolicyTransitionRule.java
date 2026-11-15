package com.psgitech.library.policy;

import com.psgitech.library.model.LoanPolicySettings;

/**
 * Pure, stateless domain transition rule for dynamic borrowing policy evaluation.
 * Encapsulates the hysteresis state machine. Contains zero database dependencies.
 *
 * Transition Matrix:
 * - If settings is null or !settings.isEnabled(): always return STANDARD.
 * - If previousState == HIGH_DEMAND:
 *     - If demand <= settings.getExitThreshold(): transition to STANDARD.
 *     - Else: remain HIGH_DEMAND (hysteresis prevents flapping when demand is moderate).
 * - If previousState == STANDARD (or null / unknown):
 *     - If demand >= settings.getEnterThreshold(): transition to HIGH_DEMAND.
 *     - Else: remain STANDARD.
 */
public final class PolicyTransitionRule {

    private PolicyTransitionRule() {
        // Prevent instantiation
    }

    /**
     * Determines the next policy state code based on the previous state, current demand, and settings.
     *
     * @param previousState The previous state code ("STANDARD", "HIGH_DEMAND", or null)
     * @param demand        The number of OTHER students waiting or holding allocations for the book
     * @param settings      The current loan policy configuration settings
     * @return The next policy state code ("STANDARD" or "HIGH_DEMAND")
     */
    public static String next(String previousState, int demand, LoanPolicySettings settings) {
        if (settings == null || !settings.isEnabled()) {
            return StandardLoanPolicy.CODE;
        }

        boolean wasHighDemand = HighDemandLoanPolicy.CODE.equalsIgnoreCase(previousState);

        if (wasHighDemand) {
            if (demand <= settings.getExitThreshold()) {
                return StandardLoanPolicy.CODE;
            }
            return HighDemandLoanPolicy.CODE;
        } else {
            if (demand >= settings.getEnterThreshold()) {
                return HighDemandLoanPolicy.CODE;
            }
            return StandardLoanPolicy.CODE;
        }
    }

    /**
     * Determines the next LoanPolicy strategy instance based on current policy, demand, and settings.
     */
    public static LoanPolicy determineNextPolicy(LoanPolicy currentPolicy, int demand, LoanPolicySettings settings) {
        String prev = (currentPolicy != null) ? currentPolicy.getCode() : StandardLoanPolicy.CODE;
        String nextCode = next(prev, demand, settings);
        if (HighDemandLoanPolicy.CODE.equals(nextCode)) {
            int days = (settings != null) ? settings.getHighDemandDays() : 5;
            return new HighDemandLoanPolicy(days);
        }
        return new StandardLoanPolicy();
    }
}
