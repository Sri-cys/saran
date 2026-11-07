package com.psgitech.library.interfaces;

import com.psgitech.library.model.Reservation;

import java.sql.Connection;

/**
 * Functional abstraction evaluating whether a candidate reservation is currently
 * eligible to receive an automatic physical copy allocation.
 */
public interface EligibilityChecker {

    /**
     * Checks if the student holding the given reservation is eligible for allocation.
     *
     * @param reservation the candidate reservation
     * @param conn the active JDBC connection inside the return/release transaction
     * @return true if the candidate meets all business criteria; false otherwise
     */
    boolean isEligible(Reservation reservation, Connection conn);

    /**
     * Returns the rejection reason description for the most recently evaluated candidate.
     */
    String getLastRejectionReason();
}

