package com.psgitech.library.interfaces;

import com.psgitech.library.model.Reservation;
import com.psgitech.library.model.ReservationQueue;

import java.sql.Connection;
import java.util.Optional;

/**
 * Strategy abstraction for selecting the next eligible reservation from a queue.
 * Keeps concrete database access and selection algorithm decoupled.
 */
public interface AllocationStrategy {

    /**
     * Inspects candidate reservations in the queue and selects the next eligible reservation.
     * Skipped candidates remain untouched in the queue.
     *
     * @param queue the FIFO queue of waiting reservations
     * @param checker the eligibility checker evaluating candidates
     * @param conn the active transaction connection
     * @return Optional containing the selected reservation, or empty if no candidate is eligible
     */
    Optional<Reservation> selectNext(ReservationQueue queue, EligibilityChecker checker, Connection conn);
}

