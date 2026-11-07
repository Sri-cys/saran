package com.psgitech.library.service;

import com.psgitech.library.config.FineConfig;
import com.psgitech.library.interfaces.FinePolicy;
import com.psgitech.library.model.FineCalculation;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Service that calculates overdue fines using a configurable FinePolicy strategy
 * and an injectable Clock for deterministic boundary and time-zone testing.
 */
public class FineCalculator {
    private final FinePolicy finePolicy;
    private final Clock clock;

    public FineCalculator() {
        this(new StandardFinePolicy(), Clock.system(FineConfig.getLibraryZone()));
    }

    public FineCalculator(FinePolicy finePolicy) {
        this(finePolicy, Clock.system(FineConfig.getLibraryZone()));
    }

    public FineCalculator(FinePolicy finePolicy, Clock clock) {
        this.finePolicy = (finePolicy != null) ? finePolicy : new StandardFinePolicy();
        this.clock = (clock != null) ? clock : Clock.system(FineConfig.getLibraryZone());
    }

    /**
     * Calculates the finalized fine when a textbook is returned on a specific date.
     */
    public FineCalculation calculateFine(LocalDate dueDate, LocalDate returnDate) {
        return finePolicy.calculateFine(dueDate, returnDate);
    }

    /**
     * Calculates the projected fine for an active unreturned borrowing relative to today.
     */
    public FineCalculation calculateProjectedFine(LocalDate dueDate) {
        LocalDate today = LocalDate.now(clock);
        return finePolicy.calculateFine(dueDate, today);
    }

    public Clock getClock() {
        return clock;
    }

    public FinePolicy getFinePolicy() {
        return finePolicy;
    }
}

