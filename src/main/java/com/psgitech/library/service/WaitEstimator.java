package com.psgitech.library.service;

import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Functional waiting time estimator for the FIFO reservation queue.
 * Pure calculation combining active loan due dates, hold allocations, and queue position.
 */
public class WaitEstimator {
    private static final Logger LOGGER = Logger.getLogger(WaitEstimator.class.getName());

    public static class WaitEstimate {
        private final Integer days;
        private final String message;

        public WaitEstimate(Integer days, String message) {
            this.days = days;
            this.message = message;
        }

        public Integer getDays() {
            return days;
        }

        public String getMessage() {
            return message;
        }
    }

    public WaitEstimate estimateWait(int bookId, int queuePosition) {
        return estimateWait(bookId, queuePosition, LocalDate.now());
    }

    public WaitEstimate estimateWait(int bookId, int queuePosition, LocalDate today) {
        if (queuePosition <= 0) {
            return new WaitEstimate(null, "Unable to estimate");
        }
        if (today == null) {
            today = LocalDate.now();
        }

        try (Connection conn = DBConnection.getConnection()) {
            // 1. Check if any copies are currently AVAILABLE on shelf
            String availSql = "SELECT COUNT(*) FROM book_copies WHERE book_id = ? AND status = 'AVAILABLE'";
            try (PreparedStatement ps = conn.prepareStatement(availSql)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return new WaitEstimate(0, "Available soon");
                    }
                }
            }

            // 2. Collect expected return dates from active loans and hold allocations
            List<LocalDate> expectedDates = new ArrayList<>();

            // Active hold allocations (will be collected or expired within 24h)
            String allocSql = "SELECT expires_at FROM allocations WHERE book_id = ? AND status = 'ALLOCATED'";
            try (PreparedStatement ps = conn.prepareStatement(allocSql)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        expectedDates.add(today.plusDays(1));
                    }
                }
            }

            // Active loans
            String loanSql = "SELECT due_date FROM borrow_transactions WHERE book_id = ? AND status = 'ACTIVE' ORDER BY due_date ASC";
            try (PreparedStatement ps = conn.prepareStatement(loanSql)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Date due = rs.getDate("due_date");
                        if (due != null) {
                            LocalDate d = due.toLocalDate();
                            expectedDates.add(d.isBefore(today) ? today : d);
                        }
                    }
                }
            }

            if (expectedDates.isEmpty()) {
                return new WaitEstimate(null, "Unable to estimate");
            }

            Collections.sort(expectedDates);

            // Compute target date for this queue position
            int index = queuePosition - 1;
            LocalDate targetDate;
            if (index < expectedDates.size()) {
                targetDate = expectedDates.get(index);
            } else {
                int cycles = index / expectedDates.size();
                int rem = index % expectedDates.size();
                targetDate = expectedDates.get(rem).plusDays(cycles * 14L);
            }

            long daysBetween = ChronoUnit.DAYS.between(today, targetDate);
            int days = (int) Math.max(0, daysBetween);

            String message;
            if (days <= 0) {
                message = "Available soon";
            } else if (days <= 3) {
                message = "Estimated 1–3 days";
            } else if (days <= 7) {
                message = "Estimated 4–7 days";
            } else {
                message = "Estimated > 7 days (~" + days + " days)";
            }

            return new WaitEstimate(days, message);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error estimating wait time for book #" + bookId, e);
            return new WaitEstimate(null, "Unable to estimate");
        }
    }
}
