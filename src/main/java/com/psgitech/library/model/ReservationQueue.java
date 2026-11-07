package com.psgitech.library.model;

import java.util.*;

/**
 * Encapsulates an in-memory First-In-First-Out (FIFO) queue of waiting reservations for a specific book.
 * Backed by a java.util.LinkedList implementing java.util.Queue.
 * The database remains the persistent source of truth; this class represents the domain queue abstraction.
 */
public class ReservationQueue {
    private final int bookId;
    private final Queue<Reservation> queue;

    public ReservationQueue(int bookId) {
        this.bookId = bookId;
        this.queue = new LinkedList<>();
    }

    public ReservationQueue(int bookId, List<Reservation> orderedReservations) {
        this(bookId);
        if (orderedReservations != null) {
            for (Reservation r : orderedReservations) {
                enqueue(r);
            }
        }
    }

    public int getBookId() {
        return bookId;
    }

    /**
     * Appends a reservation to the end of the FIFO queue.
     */
    public synchronized void enqueue(Reservation reservation) {
        if (reservation != null) {
            queue.offer(reservation);
        }
    }

    /**
     * Inspects the next reservation at the head of the FIFO queue without removing it.
     */
    public synchronized Reservation peekNext() {
        return queue.peek();
    }

    /**
     * Retrieves and removes the reservation at the head of the FIFO queue.
     */
    public synchronized Reservation poll() {
        return queue.poll();
    }

    /**
     * Removes a reservation from the queue by its ID (e.g., upon cancellation).
     */
    public synchronized boolean remove(int reservationId) {
        return queue.removeIf(r -> r.getReservationId() != null && r.getReservationId() == reservationId);
    }

    /**
     * Computes the 1-based FIFO position of a student in this queue.
     * Returns -1 if the student is not in the queue.
     */
    public synchronized int getPosition(int studentId) {
        int pos = 1;
        for (Reservation r : queue) {
            if (r.getStudentId() == studentId) {
                return pos;
            }
            pos++;
        }
        return -1;
    }

    /**
     * Returns the number of reservations currently waiting in this queue.
     */
    public synchronized int size() {
        return queue.size();
    }

    /**
     * Returns an unmodifiable snapshot list of the current queue elements in FIFO order.
     */
    public synchronized List<Reservation> getAsList() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    @Override
    public synchronized String toString() {
        return "ReservationQueue{" +
                "bookId=" + bookId +
                ", waitingCount=" + queue.size() +
                '}';
    }
}

