package com.psgitech.library.interfaces;

import com.psgitech.library.enums.Role;
import com.psgitech.library.model.Reservation;

/**
 * Abstraction interface for library queue reservation operations.
 * Decouples controller / servlet layers from specific storage or business logic implementations.
 */
public interface Reservable {
    Reservation createReservation(int studentId, int bookId);

    Reservation cancelReservation(int reservationId, int actingUserId, Role actingRole);

    int getQueuePosition(int reservationId);
}

