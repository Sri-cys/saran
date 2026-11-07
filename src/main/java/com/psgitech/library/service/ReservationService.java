package com.psgitech.library.service;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.ReservationStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.InvalidReservationException;
import com.psgitech.library.exception.ReservationNotFoundException;
import com.psgitech.library.interfaces.Reservable;
import com.psgitech.library.model.AllocationResult;
import com.psgitech.library.model.Reservation;
import com.psgitech.library.model.ReservationQueue;
import com.psgitech.library.repository.ReservationRepository;

import java.util.List;
import java.util.Map;

/**
 * Service orchestrating Book Queue Reservation operations.
 * Implements the Reservable interface, applying OOP Abstraction.
 * Enforces business validation rules and encapsulates the ReservationQueue collection.
 */
public class ReservationService implements Reservable {
    private final ReservationRepository reservationRepository;

    public ReservationService() {
        this.reservationRepository = new ReservationRepository();
    }

    public ReservationService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Override
    public Reservation createReservation(int studentId, int bookId) {
        if (studentId <= 0) {
            throw new InvalidReservationException("Valid student ID is required.");
        }
        if (bookId <= 0) {
            throw new InvalidReservationException("Valid book ID is required.");
        }

        return reservationRepository.createReservationTransaction(studentId, bookId);
    }

    @Override
    public Reservation cancelReservation(int reservationId, int actingUserId, Role actingRole) {
        if (reservationId <= 0) {
            throw new InvalidReservationException("Valid reservation ID is required.");
        }
        if (actingUserId <= 0) {
            throw new InvalidReservationException("Valid acting user ID is required.");
        }
        if (actingRole == null) {
            throw new InvalidReservationException("User role must be specified.");
        }

        Reservation existing = reservationRepository.findById(reservationId);
        if (existing == null) {
            throw new ReservationNotFoundException("Reservation #" + reservationId + " not found.");
        }

        // If the reservation is ALLOCATED, route through releaseAllocation to atomically reallocate or free the copy
        if (existing.getStatus() == ReservationStatus.ALLOCATED) {
            AllocationService allocService = new AllocationService();
            AllocationResult outcome = allocService.releaseAllocation(reservationId, actingUserId, actingRole);
            existing.setStatus(ReservationStatus.CANCELLED);
            existing.setCancelledAt(new java.sql.Timestamp(System.currentTimeMillis()));
            existing.setCancelledBy(actingUserId);
            existing.setReleaseOutcome(outcome);
            return existing;
        }

        return reservationRepository.cancelReservationTransaction(reservationId, actingUserId, actingRole);
    }

    @Override
    public int getQueuePosition(int reservationId) {
        if (reservationId <= 0) {
            return -1;
        }
        return reservationRepository.getQueuePosition(reservationId);
    }

    /**
     * Retrieves active reservations (WAITING or ALLOCATED) for a student with computed live positions.
     */
    public List<Reservation> getActiveReservationsForStudent(int studentId) {
        if (studentId <= 0) {
            throw new InvalidReservationException("Valid student ID is required.");
        }
        List<Reservation> list = reservationRepository.findActiveByStudent(studentId);
        WaitEstimator estimator = new WaitEstimator();
        for (Reservation r : list) {
            if (r.getStatus() == com.psgitech.library.enums.ReservationStatus.WAITING) {
                WaitEstimator.WaitEstimate est = estimator.estimateWait(r.getBookId(), r.getQueuePosition());
                r.setEstimatedWaitDays(est.getDays());
                r.setEstimatedWaitMessage(est.getMessage());
            }
        }
        return list;
    }

    /**
     * Retrieves completed or cancelled reservation history for a student.
     */
    public List<Reservation> getReservationHistoryForStudent(int studentId) {
        if (studentId <= 0) {
            throw new InvalidReservationException("Valid student ID is required.");
        }
        return reservationRepository.findHistoryByStudent(studentId);
    }

    /**
     * Assembles the in-memory FIFO ReservationQueue for a specific book.
     */
    public ReservationQueue getQueueForBook(int bookId) {
        if (bookId <= 0) {
            throw new InvalidReservationException("Valid book ID is required.");
        }
        List<Reservation> waitingList = reservationRepository.findQueueForBook(bookId);
        return new ReservationQueue(bookId, waitingList);
    }

    /**
     * Retrieves paginated active reservations across the entire library for staff oversight.
     */
    public List<Reservation> getAllActiveReservations(int page, int pageSize, Integer bookIdFilter) {
        int safePage = Math.max(1, page);
        int safePageSize = (pageSize <= 0 || pageSize > 100) ? 50 : pageSize;
        return reservationRepository.findAllActive(safePage, safePageSize, bookIdFilter);
    }

    /**
     * Counts active reservations for a student.
     */
    public int countActiveReservationsForStudent(int studentId) {
        if (studentId <= 0) {
            return 0;
        }
        return reservationRepository.countActiveByStudent(studentId);
    }

    /**
     * Finds active reservation of a student for a specific book, if one exists.
     */
    public Reservation getActiveReservationForStudentAndBook(int studentId, int bookId) {
        if (studentId <= 0 || bookId <= 0) {
            return null;
        }
        return reservationRepository.findActiveByStudentAndBook(studentId, bookId);
    }

    /**
     * Retrieves waiting counts for multiple books in a single batch query.
     */
    public Map<Integer, Integer> getWaitingCountsForBooks(List<Integer> bookIds) {
        return reservationRepository.getWaitingCountsForBooks(bookIds);
    }

    /**
     * Returns the maximum active reservation limit configured in institutional policy.
     */
    public int getMaxActiveReservationLimit() {
        return BorrowingPolicy.getMaxActiveReservationLimit();
    }
}

