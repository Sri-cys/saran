package com.psgitech.library.service;

import com.psgitech.library.interfaces.AllocationStrategy;
import com.psgitech.library.interfaces.EligibilityChecker;
import com.psgitech.library.model.Reservation;
import com.psgitech.library.model.ReservationQueue;

import java.sql.Connection;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Concrete implementation of AllocationStrategy using strict First-In-First-Out (FIFO) candidate evaluation.
 *
 * Algorithm:
 * 1. Iterates through the ReservationQueue in FIFO order (created_at ASC, reservation_id ASC).
 * 2. Checks each candidate's eligibility against business constraints.
 * 3. Returns the first eligible reservation encountered.
 * 4. Skipped candidates (e.g. inactive account, borrowing limit reached) are left untouched in the database.
 */
public class FIFOAllocationStrategy implements AllocationStrategy {
    private static final Logger LOGGER = Logger.getLogger(FIFOAllocationStrategy.class.getName());

    @Override
    public Optional<Reservation> selectNext(ReservationQueue queue, EligibilityChecker checker, Connection conn) {
        if (queue == null || checker == null) {
            return Optional.empty();
        }

        while (queue.size() > 0) {
            Reservation candidate = queue.poll();
            if (candidate == null) {
                continue;
            }

            boolean eligible = checker.isEligible(candidate, conn);
            if (eligible) {
                LOGGER.info("FIFOAllocationStrategy selected candidate reservation #" + candidate.getReservationId() +
                        " for student #" + candidate.getStudentId());
                return Optional.of(candidate);
            } else {
                LOGGER.info("FIFO candidate reservation #" + candidate.getReservationId() +
                        " (Student #" + candidate.getStudentId() + ") skipped: " + checker.getLastRejectionReason());
            }
        }

        return Optional.empty();
    }
}

