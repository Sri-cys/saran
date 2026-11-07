package com.psgitech.library.interfaces;

import com.psgitech.library.model.FineCalculation;
import java.time.LocalDate;

/**
 * Strategy interface defining how overdue library fines are computed.
 * Demonstrates OOP Abstraction and the Strategy Pattern.
 * Facilitates plugging in alternative fee policies (e.g. category-based or exam season rates)
 * without modifying circulation or return workflows.
 */
public interface FinePolicy {

    /**
     * Computes the overdue fine based on due date and return date.
     *
     * @param dueDate the scheduled return date
     * @param returnDate the actual return date
     * @return an immutable FineCalculation result
     */
    FineCalculation calculateFine(LocalDate dueDate, LocalDate returnDate);
}

